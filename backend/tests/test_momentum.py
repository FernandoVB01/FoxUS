from datetime import UTC, datetime, timedelta
import uuid
from sqlalchemy import select
from app.models.entities import MomentumChain, UserTechniqueStat, ExternalIntegration, ChainLink
from app.services.timing import compute_time_context


def first_advance(client, headers):
    task = client.post('/api/v1/tasks', headers=headers, json={'title': 'Preparar 12 diapositivas'}).json()
    mission = client.post('/api/v1/agents/generate-mission', headers=headers,
                          json={'task_id': task['id'], 'ignore_cooldown_for_demo': True}).json()
    session = client.post('/api/v1/sessions/start', headers=headers,
                          json={'microtask_id': mission['microtask_id'], 'hook_id': mission['hook_id'], 'source': 'android'}).json()['session']['id']
    result = client.post(f'/api/v1/sessions/{session}/complete', headers=headers, json={'source': 'android'})
    assert result.status_code == 200, result.text
    return task, session


def test_domino_retry_and_moment(client, auth_headers, db_session):
    task, session = first_advance(client, auth_headers)
    before = client.get('/api/v1/profile', headers=auth_headers).json()
    chain = before['chains'][0]
    assert chain['links'] == 1
    client.post(f'/api/v1/sessions/{session}/complete', headers=auth_headers, json={'source': 'android'})
    assert client.get('/api/v1/profile', headers=auth_headers).json()['chains'][0]['links'] == 1
    data = {'session_id': session, 'evidence_type': 'story', 'caption': 'Hice la primera diapositiva'}
    assert client.post('/api/v1/profile/moments', json=data).status_code == 401
    assert client.post('/api/v1/profile/moments', headers=auth_headers, json=data).status_code == 200
    assert client.post('/api/v1/profile/moments', headers=auth_headers, json=data).json()['already_saved']
    after = client.get('/api/v1/profile', headers=auth_headers).json()
    assert after['total_xp'] == before['total_xp'] + 10
    assert len(after['posts']) == 1
    url = f"/api/v1/profile/chains/{chain['id']}/continue"
    payload = {'target': 'Otras 6 diapositivas', 'minutes': 35}
    mission = client.post(url, headers=auth_headers, json=payload).json()
    assert mission['microtask']['estimated_seconds'] == 2100
    retry = client.post(url, headers=auth_headers, json=payload).json()
    assert retry['microtask_id'] == mission['microtask_id']
    second = client.post('/api/v1/sessions/start', headers=auth_headers,
                         json={'microtask_id': mission['microtask_id'], 'hook_id': mission['hook_id'], 'source': 'android'}).json()['session']['id']
    client.post(f'/api/v1/sessions/{second}/complete', headers=auth_headers, json={'source': 'android'})
    assert client.get('/api/v1/profile', headers=auth_headers).json()['chains'][0]['links'] == 2
    assert client.get('/api/v1/tasks').json()[0]['status'] != 'completed'
    assert sum(r.completed_count for r in db_session.scalars(select(UserTechniqueStat))) == 2


def test_expiry_calendar_and_ownership(client, auth_headers, db_session):
    _, session = first_advance(client, auth_headers)
    chain = db_session.scalar(select(MomentumChain))
    url = f'/api/v1/profile/chains/{chain.id}/continue'
    data = {'target': 'Seguir con seis diapositivas', 'minutes': 35}
    now = datetime.now(UTC)
    db_session.add(ExternalIntegration(user_id=chain.user_id, provider='google', cached_events_json=[
        dict(id='class', title='Clase', start_at=(now+timedelta(minutes=15)).isoformat(),
             end_at=(now+timedelta(hours=1)).isoformat())]))
    db_session.commit()
    assert client.post(url, headers=auth_headers, json=data).status_code == 409
    assert client.post(f'/api/v1/profile/chains/{uuid.uuid4()}/continue', headers=auth_headers, json=data).status_code == 404
    chain.expires_at = now - timedelta(seconds=1)
    db_session.commit()
    profile = client.get('/api/v1/profile', headers=auth_headers).json()
    assert profile['heat'] == 0
    assert profile['chains'][0]['status'] == 'broken'
    assert client.post(url, headers=auth_headers, json=data).status_code == 409
    bad = dict(session_id=session, evidence_type='photo', caption='Mi avance', image_base64='not-an-image')
    assert client.post('/api/v1/profile/moments', headers=auth_headers, json=bad).status_code == 422


def test_same_calendar_day_is_not_just_eight_hours():
    now = datetime(2026, 10, 30, 12, tzinfo=UTC)
    assert compute_time_context(now_utc=now, deadline=now+timedelta(hours=14)).urgency_phase == 'same_day'
    assert compute_time_context(now_utc=now, deadline=now+timedelta(days=4)).urgency_phase == 'early'


def test_dashboard_measured_speed_and_duplicate_evidence(client, auth_headers, db_session):
    from app.models.entities import SessionEvent
    _, session = first_advance(client, auth_headers)
    event = db_session.scalar(select(SessionEvent).where(
        SessionEvent.session_id == uuid.UUID(session),
        SessionEvent.event_type == 'MICROTASK_COMPLETED_SELF_REPORTED'))
    event.event_metadata = dict(event.event_metadata or {}, actual_duration_seconds=600)
    db_session.commit()
    payload = dict(session_id=session, evidence_type='story', caption='Seis diapositivas listas',
                   units_completed=6, unit='diapositivas')
    for _ in range(2):
        assert client.post('/api/v1/profile/moments', headers=auth_headers, json=payload).status_code == 200
    profile = client.get('/api/v1/profile', headers=auth_headers).json()
    assert profile['dashboard'] == dict(period_days=30, focus_minutes=10, advances=1,
        activities=1, completion_rate=100, verified=0,
        speeds=[dict(unit='diapositivas', per_hour=36.0)])
    assert profile['chains'][0]['suggestion'] == '¿Puedes hacer 6 diapositivas más?'


def test_stopwatch_respects_calendar_and_retries(client, auth_headers, db_session):
    first_advance(client, auth_headers)
    chain = db_session.scalar(select(MomentumChain))
    now = datetime.now(UTC)
    db_session.add(ExternalIntegration(user_id=chain.user_id, provider='google', cached_events_json=[
        dict(id='class', title='Clase', start_at=(now+timedelta(minutes=20)).isoformat(),
             end_at=(now+timedelta(hours=1)).isoformat())]))
    db_session.commit()
    url = f'/api/v1/profile/chains/{chain.id}/continue'
    payload = dict(target='Hacer seis diapositivas', minutes=120, timer_mode='stopwatch')
    response = client.post(url, headers=auth_headers, json=payload)
    assert response.status_code == 200, response.text
    mission = response.json()
    assert mission['timer_mode'] == 'stopwatch'
    assert 0 < mission['microtask']['estimated_seconds'] <= 15 * 60
    retry = client.post(url, headers=auth_headers, json=payload).json()
    assert retry['microtask_id'] == mission['microtask_id']
    assert retry['timer_mode'] == 'stopwatch'


def test_media_roundtrip_and_auth(client, auth_headers):
    import base64
    _, session = first_advance(client, auth_headers)
    media = b'RIFF' + b'\x00' * 4 + b'WAVE' + b'fmt ' + b'\x00' * 32
    payload = dict(session_id=session, evidence_type='podcast', caption='Cómo hice el esquema',
                   media_mime='audio/wav', media_base64=base64.b64encode(media).decode())
    result = client.post('/api/v1/profile/moments', headers=auth_headers, json=payload)
    assert result.status_code == 200, result.text
    url = f"/api/v1/profile/moments/{result.json()['id']}/media"
    assert client.get(url).status_code == 401
    assert client.get(url, headers=auth_headers).content == media
    assert client.get('/api/v1/profile').status_code == 401
    posts = client.get('/api/v1/profile', headers=auth_headers).json()['posts']
    assert posts[0]['media_mime'] == 'audio/wav'
    assert 'media_base64' not in posts[0]
