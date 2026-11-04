/* FHIRCare — app.js : sign in, create account, patient / doctor / admin dashboards.
   Works with home.js (window.Home) and the Spring Boot REST API under /api. */
(function () {
  "use strict";
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const esc = v => String(v == null ? "" : v).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const fmt = d => (d ? String(d).replace("T", " ").slice(0, 16) : "—");
  const KEY = "fhircare_user";

  let user = null;      // AuthResponse from server
  let view = "";        // current tab inside dashboard
  let ctx = {};         // per-view cache (patient, selected patient, etc.)

  // ------------------------------------------------------------------ helpers
  function toast(msg, bad) {
    const t = $("#toast"); if (!t) return;
    t.textContent = msg; t.classList.remove("hidden");
    t.style.background = bad ? "#b3261e" : "";
    clearTimeout(toast._t); toast._t = setTimeout(() => t.classList.add("hidden"), 3500);
  }

  async function api(path, opts) {
    opts = opts || {};
    const init = { method: opts.method || "GET", headers: {} };
    if (opts.body !== undefined) { init.headers["Content-Type"] = "application/json"; init.body = JSON.stringify(opts.body); }
    let res;
    try { res = await fetch("/api" + path, init); }
    catch (e) { throw new Error("Cannot reach the server. If it is on a free plan, wait ~50 seconds and try again."); }
    let data = null;
    const text = await res.text();
    try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }
    if (!res.ok) {
      const msg = data && data.error ? data.error : (data && data.message) || ("Request failed (" + res.status + ")");
      const err = new Error(msg); err.status = res.status; throw err;
    }
    return data;
  }

  function badge(s) { return `<span class="badge ${esc(String(s).toLowerCase())}">${esc(s)}</span>`; }
  function empty(msg) { return `<p class="empty">${esc(msg)}</p>`; }

  // ------------------------------------------------------------------ auth screen
  $$(".tab").forEach(t => t.addEventListener("click", () => {
    $$(".tab").forEach(x => x.classList.toggle("active", x === t));
    $("#login-form").classList.toggle("hidden", t.dataset.tab !== "login");
    $("#register-form").classList.toggle("hidden", t.dataset.tab !== "register");
  }));

  $("#reg-role").addEventListener("change", e => {
    const doc = e.target.value === "DOCTOR";
    $("#reg-patient-fields").classList.toggle("hidden", doc);
    $("#reg-doctor-fields").classList.toggle("hidden", !doc);
  });

  $("#login-form").addEventListener("submit", async e => {
    e.preventDefault();
    const err = $("#login-error"); err.textContent = "";
    const btn = $("button[type=submit]", e.target); btn.disabled = true;
    try {
      const u = await api("/auth/login", { method: "POST", body: { username: $("#login-username").value.trim(), password: $("#login-password").value } });
      setUser(u); showApp();
    } catch (ex) { err.textContent = ex.message; }
    btn.disabled = false;
  });

  $("#register-form").addEventListener("submit", async e => {
    e.preventDefault();
    const err = $("#register-error"); err.textContent = "";
    const pw = $("#reg-password").value;
    if (pw.length < 6) { err.textContent = "Password must be at least 6 characters."; return; }
    const role = $("#reg-role").value;
    const body = {
      role, fullName: $("#reg-fullname").value.trim(), username: $("#reg-username").value.trim(),
      email: $("#reg-email").value.trim(), password: pw
    };
    if (role === "PATIENT") { body.gender = $("#reg-gender").value; body.birthDate = $("#reg-birthdate").value || null; }
    else body.specialization = $("#reg-specialization").value.trim();
    const btn = $("button[type=submit]", e.target); btn.disabled = true;
    try {
      const u = await api("/auth/register", { method: "POST", body });
      toast("Account created. Welcome, " + u.fullName + "!");
      setUser(u); showApp();
    } catch (ex) { err.textContent = ex.message; }
    btn.disabled = false;
  });

  function setUser(u) {
    user = u;
    if (u) localStorage.setItem(KEY, JSON.stringify(u)); else localStorage.removeItem(KEY);
    if (window.Home) Home.setUser(u);
  }

  function logout() {
    setUser(null); ctx = {}; view = "";
    $("#login-form").reset();
    if (window.Home) Home.show();
  }

  // ------------------------------------------------------------------ app shell
  const NAV = {
    PATIENT: [["overview", "My Records"], ["requests", "Access Requests"], ["consents", "Consents"], ["profile", "My Profile"]],
    DOCTOR: [["patients", "My Patients"], ["request", "Request Access"], ["myreq", "My Requests"], ["addpatient", "Add Patient"]],
    ADMIN: [["dash", "Dashboard"], ["users", "Users"], ["audit", "Audit Log"]]
  };

  function showApp() {
    if (!user) { if (window.Home) Home.openAuth("login"); return; }
    if (window.Home) Home.hide();
    $("#auth-screen").classList.add("hidden");
    $("#app-shell").classList.remove("hidden");
    $("#site-footer").classList.remove("hidden");
    $("#role-chip").textContent = user.role;
    $("#nav-name").textContent = user.fullName;
    $("#nav-role").textContent = user.role + (user.profileCode ? " · " + user.profileCode : "");
    $("#nav-avatar").textContent = user.fullName.split(/\s+/).map(w => w[0]).slice(0, 2).join("").toUpperCase();

    const nav = $("#role-nav"); nav.innerHTML = "";
    NAV[user.role].forEach(([id, label]) => {
      const b = document.createElement("button"); b.textContent = label; b.dataset.view = id;
      b.onclick = () => go(id); nav.appendChild(b);
    });
    const h = document.createElement("button"); h.className = "nav-home"; h.textContent = "← Home";
    h.onclick = () => Home.goHome(); nav.appendChild(h);
    go(NAV[user.role][0][0]);
    window.scrollTo(0, 0);
  }
  window.showApp = showApp;

  $("#logout-btn").onclick = logout;
  $("#app-brand").onclick = e => { e.preventDefault(); if (user) go(NAV[user.role][0][0]); };

  async function go(id) {
    view = id;
    $$("#role-nav button[data-view]").forEach(b => b.classList.toggle("active", b.dataset.view === id));
    const root = $("#view-root");
    root.innerHTML = '<p class="empty">Loading…</p>';
    try {
      const fn = VIEWS[user.role][id];
      root.innerHTML = await fn();
      const after = AFTER[user.role + ":" + id]; if (after) after(root);
    } catch (ex) {
      root.innerHTML = `<div class="card"><p class="error">${esc(ex.message)}</p></div>`;
    }
  }

  // generic click delegation
  document.addEventListener("click", async e => {
    const el = e.target.closest("[data-act]"); if (!el || !user) return;
    const act = el.dataset.act, id = el.dataset.id;
    try { await ACTIONS[act](el, id); } catch (ex) { toast(ex.message, true); }
  });

  function recordsTable(list, canDelete) {
    if (!list.length) return empty("No records yet.");
    return `<table style="width:100%;border-collapse:collapse;font-size:13px">
      <tr style="text-align:left;color:var(--muted)"><th>Type</th><th>Title</th><th>Details</th><th>Value</th><th>Date</th>${canDelete ? "<th></th>" : ""}</tr>
      ${list.map(r => `<tr style="border-top:1px solid var(--line)">
        <td style="padding:8px 4px"><span class="tag">${esc(r.recordType)}</span></td><td>${esc(r.title)}</td><td>${esc(r.description)}</td>
        <td>${esc(r.valueText || "")} ${esc(r.unit || "")}</td><td>${esc(r.recordDate || "")}</td>
        ${canDelete ? `<td><button class="text-btn" data-act="delrec" data-id="${r.recordId}">Delete</button></td>` : ""}</tr>`).join("")}
    </table>`;
  }

  function recordForm(patientId) {
    return `<form id="rec-form" class="auth-form" data-pid="${patientId}">
      <label>Type</label><select id="rf-type">${["CONDITION", "OBSERVATION", "MEDICATION", "ALLERGY", "IMMUNIZATION", "PROCEDURE", "REPORT"].map(t => `<option>${t}</option>`).join("")}</select>
      <label>Title</label><input id="rf-title" required placeholder="e.g. Blood pressure" />
      <label>Details</label><input id="rf-desc" placeholder="Description" />
      <label>Value</label><input id="rf-value" placeholder="e.g. 120/80" />
      <label>Unit</label><input id="rf-unit" placeholder="e.g. mmHg" />
      <label>Date</label><input id="rf-date" type="date" value="${new Date().toISOString().slice(0, 10)}" />
      <button class="primary full" type="submit">Add record</button></form>`;
  }

  // ------------------------------------------------------------------ views
  const VIEWS = { PATIENT: {}, DOCTOR: {}, ADMIN: {} };
  const AFTER = {};

  // ---- PATIENT
  VIEWS.PATIENT.overview = async () => {
    const p = await api("/patients/" + user.profileId); ctx.patient = p;
    const recs = await api("/records/patient/" + p.patientId);
    return `<h2>Welcome, ${esc(user.fullName)}</h2>
      <p class="section-sub">Patient code <b>${esc(p.patientCode)}</b> — share it with a doctor so they can request access.</p>
      <div class="grid-3" style="margin:18px 0">
        <div class="stat indigo"><b>${recs.length}</b><span>Medical records</span></div>
        <div class="stat"><b>${esc(p.bloodGroup || "—")}</b><span>Blood group</span></div>
        <div class="stat amber"><b>${esc(p.gender || "—")}</b><span>Gender</span></div></div>
      <div class="card"><div class="card-head"><h3>My medical records</h3><span>
        <button class="ghost" data-act="fhir" data-id="${esc(p.patientCode)}">View FHIR JSON</button>
        <button class="primary" data-act="pdf" data-id="${p.patientId}">Download PDF</button></span></div>
        ${recordsTable(recs, false)}</div>`;
  };

  VIEWS.PATIENT.requests = async () => {
    const list = await api("/access-requests/patient/" + user.profileId);
    const docs = await api("/doctors"); const dn = {}; docs.forEach(d => dn[d.doctorId] = `Dr. ${d.firstName} ${d.lastName}`);
    return `<h2>Access requests</h2><p class="section-sub">Doctors who asked to see your records.</p>
      <div class="card">${!list.length ? empty("No requests yet.") : list.map(r => `
        <div class="row" style="display:flex;justify-content:space-between;gap:12px;padding:12px 0;border-top:1px solid var(--line)">
          <div><b>${esc(dn[r.requestingDoctorId] || "Doctor #" + r.requestingDoctorId)}</b> ${badge(r.status)}<br>
            <small>${esc(r.reason || "")} · ${fmt(r.requestedAt)}</small>
            ${r.status === "APPROVED" && !r.codeVerified ? `<br><span class="code-badge">Verification code: <b>${esc(r.verificationCode)}</b></span> <small>Give this code to the doctor.</small>` : ""}
            ${r.status === "APPROVED" && r.codeVerified ? `<br><small>Doctor verified — access active.</small>` : ""}</div>
          <div>${r.status === "PENDING" ? `<button class="primary" data-act="decide" data-id="${r.requestId}" data-d="APPROVED">Approve</button>
            <button class="danger-btn" data-act="decide" data-id="${r.requestId}" data-d="REJECTED">Reject</button>` : ""}</div></div>`).join("")}</div>`;
  };

  VIEWS.PATIENT.consents = async () => {
    const list = await api("/access-requests/consents/patient/" + user.profileId);
    const docs = await api("/doctors"); const dn = {}; docs.forEach(d => dn[d.doctorId] = `Dr. ${d.firstName} ${d.lastName}`);
    return `<h2>Consents</h2><p class="section-sub">Who can currently see your data. Revoke any time.</p>
      <div class="card">${!list.length ? empty("No consents yet.") : list.map(c => `
        <div style="display:flex;justify-content:space-between;padding:12px 0;border-top:1px solid var(--line)">
          <div><b>${esc(dn[c.doctorId] || "Doctor #" + c.doctorId)}</b> ${badge(c.status)}<br><small>${esc(c.purpose || "")} · granted ${fmt(c.grantedAt)}</small></div>
          <div>${c.status === "GRANTED" ? `<button class="danger-btn" data-act="revoke" data-id="${c.consentId}">Revoke</button>` : ""}</div></div>`).join("")}</div>`;
  };

  VIEWS.PATIENT.profile = async () => {
    const p = await api("/patients/" + user.profileId); ctx.patient = p;
    const locked = !!p.profileSubmitted && !p.profileEditUnlocked;
    const f = (id, label, val, type) => `<label>${label}</label><input id="pf-${id}" type="${type || "text"}" value="${esc(val || "")}" ${locked ? "disabled" : ""} />`;
    return `<h2>My profile</h2><div class="card"><form id="profile-form" class="auth-form">
      ${f("firstName", "First name", p.firstName)}${f("lastName", "Last name", p.lastName)}
      ${f("birthDate", "Date of birth", p.birthDate, "date")}${f("bloodGroup", "Blood group", p.bloodGroup)}
      ${f("phone", "Phone", p.phone)}${f("city", "City", p.city)}${f("state", "State", p.state)}${f("address", "Address", p.address)}
      ${locked ? '<p class="empty">Your profile was saved and is now locked. A doctor must unlock one edit for you.</p>' : '<button class="primary full" type="submit">Save profile</button>'}
      <p class="error" id="profile-error"></p></form></div>`;
  };
  AFTER["PATIENT:profile"] = root => {
    $("#profile-form", root).addEventListener("submit", async e => {
      e.preventDefault();
      const p = Object.assign({}, ctx.patient);
      ["firstName", "lastName", "birthDate", "bloodGroup", "phone", "city", "state", "address"].forEach(k => { p[k] = $("#pf-" + k).value || null; });
      try { await api("/patients/" + p.patientId, { method: "PUT", body: p }); toast("Profile saved."); go("profile"); }
      catch (ex) { $("#profile-error").textContent = ex.message; }
    });
  };

  // ---- DOCTOR
  VIEWS.DOCTOR.patients = async () => {
    const consents = await api("/access-requests/consents/doctor/" + user.profileId);
    const ids = [...new Set(consents.map(c => c.patientId))];
    const pts = await Promise.all(ids.map(i => api("/patients/" + i).catch(() => null)));
    ctx.patients = pts.filter(Boolean);
    return `<h2>My patients</h2><p class="section-sub">Patients who granted you access.</p>
      <div class="card">${!ctx.patients.length ? empty("No patients yet. Request access or add a patient.") :
        ctx.patients.map(p => `<div style="display:flex;justify-content:space-between;padding:12px 0;border-top:1px solid var(--line)">
          <div><b>${esc(p.firstName)} ${esc(p.lastName)}</b> <span class="tag">${esc(p.patientCode)}</span><br><small>${esc(p.gender || "")} ${esc(p.birthDate || "")}</small></div>
          <div><button class="ghost" data-act="openpatient" data-id="${p.patientId}">Open records</button></div></div>`).join("")}</div>
      <div id="patient-detail"></div>`;
  };

  VIEWS.DOCTOR.request = async () => `<h2>Request access</h2><div class="card"><form id="req-form" class="auth-form">
      <label>Patient code</label><input id="rq-code" required placeholder="PAT-1001" />
      <label>Reason</label><input id="rq-reason" required placeholder="Follow-up consultation" />
      <button class="primary full" type="submit">Send request</button><p class="error" id="req-error"></p></form></div>`;
  AFTER["DOCTOR:request"] = root => {
    $("#req-form", root).addEventListener("submit", async e => {
      e.preventDefault();
      try {
        await api("/access-requests", { method: "POST", body: { patientCode: $("#rq-code").value.trim(), requestingDoctorId: user.profileId, reason: $("#rq-reason").value.trim() } });
        toast("Request sent. Wait for the patient to approve."); go("myreq");
      } catch (ex) { $("#req-error").textContent = ex.message; }
    });
  };

  VIEWS.DOCTOR.myreq = async () => {
    const list = await api("/access-requests/doctor/" + user.profileId);
    const pts = await api("/patients"); const pn = {}; pts.forEach(p => pn[p.patientId] = p.firstName + " " + p.lastName + " (" + p.patientCode + ")");
    return `<h2>My requests</h2><div class="card">${!list.length ? empty("No requests yet.") : list.map(r => `
      <div style="padding:12px 0;border-top:1px solid var(--line)">
        <b>${esc(pn[r.patientId] || "Patient #" + r.patientId)}</b> ${badge(r.status)}<br><small>${esc(r.reason || "")} · ${fmt(r.requestedAt)}</small>
        ${r.status === "APPROVED" && !r.codeVerified ? `<div class="verify-inline" style="margin-top:8px"><input id="code-${r.requestId}" placeholder="6-digit code from patient" maxlength="6" />
          <button class="primary" data-act="verify" data-id="${r.requestId}">Verify</button></div>` : ""}
        ${r.codeVerified ? "<br><small>Access active.</small>" : ""}</div>`).join("")}</div>`;
  };

  VIEWS.DOCTOR.addpatient = async () => {
    const f = (id, label, ph, type, req) => `<label>${label}</label><input id="np-${id}" type="${type || "text"}" ${req ? "required" : ""} placeholder="${ph || ""}" />`;
    return `<h2>Add patient</h2><div class="card"><form id="np-form" class="auth-form">
      ${f("firstName", "First name", "", "", 1)}${f("lastName", "Last name", "", "", 1)}
      <label>Gender</label><select id="np-gender"><option value="female">Female</option><option value="male">Male</option><option value="other">Other</option></select>
      ${f("birthDate", "Date of birth", "", "date")}${f("bloodGroup", "Blood group", "O+")}${f("phone", "Phone")}${f("email", "Email", "", "email")}${f("city", "City")}${f("state", "State")}${f("address", "Address")}
      <button class="primary full" type="submit">Add patient</button><p class="error" id="np-error"></p></form></div>`;
  };
  AFTER["DOCTOR:addpatient"] = root => {
    $("#np-form", root).addEventListener("submit", async e => {
      e.preventDefault();
      const b = {}; ["firstName", "lastName", "gender", "birthDate", "bloodGroup", "phone", "email", "city", "state", "address"].forEach(k => b[k] = $("#np-" + k).value || null);
      try { const p = await api("/patients?createdByDoctorId=" + user.profileId, { method: "POST", body: b }); toast("Patient added: " + (p.patientCode || "")); go("patients"); }
      catch (ex) { $("#np-error").textContent = ex.message; }
    });
  };

  // ---- ADMIN
  VIEWS.ADMIN.dash = async () => {
    const s = await api("/admin/stats");
    const t = s.recordsByType || {};
    return `<h2>Admin dashboard</h2><div class="grid-4" style="margin:18px 0">
      <div class="stat indigo"><b>${s.patients}</b><span>Patients</span></div><div class="stat"><b>${s.doctors}</b><span>Doctors</span></div>
      <div class="stat amber"><b>${s.records}</b><span>Records</span></div><div class="stat"><b>${s.pendingRequests}</b><span>Pending requests</span></div>
      <div class="stat"><b>${s.activeConsents}</b><span>Active consents</span></div><div class="stat"><b>${s.fhirResources}</b><span>FHIR resources</span></div>
      <div class="stat"><b>${s.auditEvents}</b><span>Audit events</span></div></div>
      <div class="card"><div class="card-head"><h3>Records by type</h3></div>${Object.keys(t).length ? Object.entries(t).map(([k, v]) => `<p><span class="tag">${esc(k)}</span> ${v}</p>`).join("") : empty("No records yet.")}</div>`;
  };

  VIEWS.ADMIN.users = async () => {
    const list = await api("/admin/users");
    return `<h2>Users</h2><div class="card"><table style="width:100%;border-collapse:collapse;font-size:13px">
      <tr style="text-align:left;color:var(--muted)"><th>Username</th><th>Name</th><th>Role</th><th>Status</th><th>Last login</th><th></th></tr>
      ${list.map(u => `<tr style="border-top:1px solid var(--line)"><td style="padding:8px 4px">${esc(u.username)}</td><td>${esc(u.fullName)}</td><td>${esc(u.role)}</td><td>${badge(u.status)}</td><td>${fmt(u.lastLogin)}</td>
        <td>${u.role === "ADMIN" ? "" : u.status === "ACTIVE" ? `<button class="danger-btn" data-act="status" data-id="${u.userId}" data-s="LOCKED">Lock</button>` : `<button class="primary" data-act="status" data-id="${u.userId}" data-s="ACTIVE">Activate</button>`}</td></tr>`).join("")}</table></div>`;
  };

  VIEWS.ADMIN.audit = async () => {
    const list = await api("/admin/audit-logs");
    return `<h2>Audit log</h2><div class="card" style="overflow-x:auto"><table style="width:100%;border-collapse:collapse;font-size:12.5px">
      <tr style="text-align:left;color:var(--muted)"><th>Time</th><th>Role</th><th>Action</th><th>Resource</th><th>Description</th></tr>
      ${list.slice(0, 300).map(a => `<tr style="border-top:1px solid var(--line)"><td style="padding:7px 4px">${fmt(a.eventTime)}</td><td>${esc(a.userRole)}</td>
        <td><span class="tag">${esc(a.action)}</span></td><td>${esc(a.resourceType)} ${esc(a.resourceId || "")}</td><td>${esc(a.description)}</td></tr>`).join("")}</table></div>`;
  };

  // ------------------------------------------------------------------ actions
  const ACTIONS = {
    async decide(el, id) {
      const r = await api("/access-requests/" + id + "/decision", { method: "PUT", body: { decision: el.dataset.d } });
      toast(el.dataset.d === "APPROVED" ? "Approved. Give the code to the doctor." : "Request rejected."); go("requests");
    },
    async revoke(el, id) { await api("/access-requests/consents/" + id + "/revoke", { method: "PUT" }); toast("Consent revoked."); go("consents"); },
    async verify(el, id) {
      const code = ($("#code-" + id) || {}).value || "";
      await api("/access-requests/" + id + "/verify-code", { method: "PUT", body: { code } });
      toast("Access activated."); go("myreq");
    },
    async status(el, id) { await api("/admin/users/" + id + "/status", { method: "PUT", body: { status: el.dataset.s } }); toast("User updated."); go("users"); },
    async fhir(el, code) {
      const b = await api("/fhir/patient/" + encodeURIComponent(code));
      $("#fhir-json").textContent = JSON.stringify(b, null, 2); $("#fhir-modal").classList.remove("hidden");
    },
    async pdf(el, id) {
      const q = user.role === "DOCTOR" ? "?doctorId=" + user.profileId : "";
      window.open("/api/reports/patient/" + id + "/pdf" + q, "_blank");
    },
    async delrec(el, id) {
      if (!confirm("Delete this record?")) return;
      await api("/records/" + id, { method: "DELETE" }); toast("Record deleted."); if (ctx.openPid) ACTIONS.openpatient(null, ctx.openPid);
    },
    async openpatient(el, id) {
      ctx.openPid = id;
      const p = ctx.patients.find(x => String(x.patientId) === String(id));
      const recs = await api("/records/patient/" + id + "?doctorId=" + user.profileId);
      const box = $("#patient-detail");
      box.innerHTML = `<div class="card" style="margin-top:18px"><div class="card-head"><h3>${esc(p.firstName)} ${esc(p.lastName)} — records</h3><span>
        <button class="ghost" data-act="fhir" data-id="${esc(p.patientCode)}">FHIR JSON</button>
        <button class="primary" data-act="pdf" data-id="${p.patientId}">PDF</button></span></div>
        ${recordsTable(recs, true)}<h3 style="margin-top:22px">Add a record</h3>${recordForm(p.patientId)}</div>`;
      $("#rec-form", box).addEventListener("submit", async e => {
        e.preventDefault();
        const body = { patientId: Number(id), recordType: $("#rf-type").value, title: $("#rf-title").value.trim(), description: $("#rf-desc").value,
          valueText: $("#rf-value").value, unit: $("#rf-unit").value, recordDate: $("#rf-date").value || null, status: "ACTIVE" };
        try { await api("/records?actingDoctorId=" + user.profileId, { method: "POST", body }); toast("Record added."); ACTIONS.openpatient(null, id); }
        catch (ex) { toast(ex.message, true); }
      });
      box.scrollIntoView({ behavior: "smooth" });
    }
  };

  $("#modal-close").onclick = () => $("#fhir-modal").classList.add("hidden");

  // ------------------------------------------------------------------ boot
  try { const saved = JSON.parse(localStorage.getItem(KEY) || "null"); if (saved && saved.userId) { user = saved; if (window.Home) Home.setUser(user); } } catch (e) { /* ignore */ }
})();