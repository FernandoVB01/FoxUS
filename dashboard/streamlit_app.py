from __future__ import annotations

import os
import httpx
import streamlit as st

st.set_page_config(
    page_title="FOXUS — Multi-Agent & Gemma 4 Monitor",
    page_icon="🦊",
    layout="wide",
)

BACKEND_URL = st.sidebar.text_input(
    "Backend API Base URL",
    value=os.getenv("FOXUS_BACKEND_URL", "http://localhost:8000"),
).rstrip("/")
DEMO_TOKEN = st.sidebar.text_input(
    "X-Demo-Token",
    value=os.getenv("FOXUS_DEMO_TOKEN", "foxus-demo-dev-2026"),
    type="password",
)

headers = {"X-Demo-Token": DEMO_TOKEN} if DEMO_TOKEN else {}

st.title("🦊 FOXUS — Panel Técnico Multiagente (Google Gemma 4)")
st.caption("Observabilidad auxiliar para jueces y demostración en vivo (Android + Backend + ESP32)")

col_refresh, col_pressure = st.columns([1, 2])
if col_refresh.button("🔄 Actualizar telemetría"):
    st.rerun()

try:
    with httpx.Client(timeout=20.0) as client:
        health = client.get(f"{BACKEND_URL}/api/v1/health").json()
        rewards = client.get(f"{BACKEND_URL}/api/v1/rewards/profile", headers=headers).json()
        tasks = client.get(f"{BACKEND_URL}/api/v1/tasks", headers=headers).json()
        google_status = client.get(
            f"{BACKEND_URL}/api/v1/integrations/google/status", headers=headers
        ).json()
        devices = client.get(f"{BACKEND_URL}/api/v1/devices", headers=headers).json()
        sessions = client.get(f"{BACKEND_URL}/api/v1/sessions/history", headers=headers).json()
except Exception as exc:
    st.error(f"No se pudo conectar al backend en {BACKEND_URL}: {exc}")
    st.stop()

m1, m2, m3, m4 = st.columns(4)
m1.metric("Modelo Central IA", health.get("gemma_model", "gemma-4-26b-a4b-it"))
m2.metric("XP Total / Nivel", f"{rewards.get('total_xp', 0)} XP (Nv. {rewards.get('level', 1)})")
m3.metric("Tareas Activas", str(len(tasks)))
m4.metric(
    "Google Sync",
    "Conectado" if google_status.get("connected") else "Modo Local / Listo",
)

if col_pressure.button("⚡ Evaluar Modo Presión con Gemma 4 en vivo"):
    with httpx.Client(timeout=45.0) as client:
        res = client.post(
            f"{BACKEND_URL}/api/v1/agents/evaluate-pressure",
            headers=headers,
            json={"demo_force": True},
        ).json()
    st.success(f"Urgencia: {res.get('urgency_score')}/100 — {res.get('reason_short')}")
    mission = res.get("mission") or {}
    micro = mission.get("microtask") or {}
    st.markdown(f"**Microacción:** {micro.get('title')}")
    for step in micro.get("steps") or []:
        st.markdown(f"- {step}")
    with st.expander("Ver traza completa de los 4 Agentes + Gemma 4"):
        st.json(mission.get("agent_trace") or res)

c_left, c_right = st.columns(2)
with c_left:
    st.subheader("📋 Tareas y Nivel de Presión")
    for t in tasks:
        badge = "🔥 Presión admitida" if t.get("needs_pressure") else "🌿 Normal"
        st.markdown(
            f"- **{t.get('title')}** — `{badge}` · origen: `{t.get('source')}` · estado: `{t.get('status')}`"
        )

with c_right:
    st.subheader("⌚ Dispositivos ESP32 y Simulador")
    if not devices:
        st.info("Sin dispositivos registrados todavía.")
    for d in devices:
        st.markdown(
            f"- **{d.get('device_name')}** (`{d.get('device_type')}`) — estado: `{d.get('status')}`"
        )

st.subheader("🕒 Historial Reciente de Sesiones")
st.dataframe(sessions, use_container_width=True)
