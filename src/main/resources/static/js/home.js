/* ============================================================
   FHIRCare home page + shared shell (header/footer/theme)
   Pure vanilla JS. Loaded BEFORE app.js.
   Exposes: window.Home { show(), hide(), openAuth(tab) }
   ============================================================ */
(function () {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  // ---------------------------------------------------------- theme (shared by home + app + admin)
  function applyTheme(t) {
    document.documentElement.setAttribute("data-theme", t);
    localStorage.setItem("fhircare_theme", t);
    $$("#theme-toggle, #theme-toggle-app").forEach(b => (b.textContent = t === "dark" ? "☀️" : "🌙"));
  }
  applyTheme(localStorage.getItem("fhircare_theme") ||
    (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"));
  ["theme-toggle", "theme-toggle-app"].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.onclick = () => applyTheme(document.documentElement.getAttribute("data-theme") === "dark" ? "light" : "dark");
  });

  // ---------------------------------------------------------- shared footer
  function renderFooter() {
    const f = document.getElementById("site-footer");
    f.innerHTML = `
      <div class="foot-grid">
        <div class="foot-brand">
          <div class="brand"><span class="logo">✚</span><span>FHIR<span>Care</span></span></div>
          <p>Secure, consent-first patient medical record exchange built on HL7 FHIR R5 concepts.</p>
        </div>
        <div><h4>Platform</h4>
          <a href="#" data-foot="features">Features</a>
          <a href="#" data-foot="how">How it works</a>
          <a href="#" data-foot="fhir">FHIR playground</a>
          <a href="#" data-foot="roles">Dashboards</a></div>
        <div><h4>Resources</h4>
          <a href="#" data-foot="security">Security</a>
          <a href="#" data-foot="faq">FAQ</a>
          <a href="/api/fhir/patient/PAT-1001" target="_blank" rel="noopener">Sample FHIR API</a></div>
        <div><h4>System status</h4>
          <p class="status"><i class="dot" id="status-dot"></i><span id="status-text">Checking API…</span></p>
          <p class="mini">Academic / portfolio project — not a production clinical system.</p></div>
      </div>
      <div class="foot-bottom">
        <span>© ${new Date().getFullYear()} FHIRCare · Consent‑first health data exchange</span>
        <span>Built with Java · Spring Boot · MySQL · Vanilla JS</span>
      </div>`;
    $$("[data-foot]", f).forEach(a => a.onclick = e => {
      e.preventDefault();
      if (window.Home.goHome) window.Home.goHome(a.dataset.foot);
    });
  }
  renderFooter();

  // live API health + live record count (graceful if backend is down / not admin)
  async function pingApi() {
    const dot = $("#status-dot"), txt = $("#status-text");
    try {
      const r = await fetch("/api/patients", { cache: "no-store" });
      if (!r.ok) throw 0;
      const list = await r.json();
      dot.className = "dot ok"; txt.textContent = "All systems operational";
      const stat = $("#live-stat");
      if (stat) {
        stat.dataset.count = list.length; $("#live-stat-label").textContent = "Patients registered";
        stat.dataset.done = ""; countUp(stat);
      }
    } catch (e) {
      dot.className = "dot bad"; txt.textContent = "API unreachable";
    }
  }

  // ---------------------------------------------------------- hero: typing word
  const words = ["secure", "portable", "consent-driven", "interoperable", "auditable"];
  (function typing() {
    const el = $("#typed-word"); if (!el) return;
    if (reduceMotion) { el.textContent = words[0]; return; }
    let w = 0, c = 0, del = false;
    (function tick() {
      const word = words[w];
      el.textContent = word.slice(0, c);
      if (!del && c === word.length) { del = true; return setTimeout(tick, 1400); }
      if (del && c === 0) { del = false; w = (w + 1) % words.length; }
      c += del ? -1 : 1;
      setTimeout(tick, del ? 45 : 85);
    })();
  })();

  // ---------------------------------------------------------- hero: animated FHIR code
  const heroLines = [
    '{',
    '  "resourceType": "Bundle",',
    '  "type": "collection",',
    '  "entry": [',
    '    { "resource": {',
    '        "resourceType": "Patient",',
    '        "id": "PAT-1001",',
    '        "name": [{ "family": "Sharma" }]',
    '    }},',
    '    { "resource": {',
    '        "resourceType": "Condition",',
    '        "clinicalStatus": "active",',
    '        "code": { "text": "Hypertension" }',
    '    }}',
    '  ]',
    '}'
  ];
  function highlight(json) {
    return json.replace(/("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false|null)\b|(-?\d+\.?\d*)/g, (m, str, colon, bool, num) => {
      if (str) return colon ? `<span class="k">${str}</span>${colon}` : `<span class="s">${str}</span>`;
      if (bool) return `<span class="b">${m}</span>`;
      return `<span class="n">${m}</span>`;
    });
  }
  (function heroCode() {
    const pre = $("#hero-code"); if (!pre) return;
    if (reduceMotion) { pre.innerHTML = highlight(heroLines.join("\n")); return; }
    let i = 0;
    (function next() {
      pre.innerHTML = highlight(heroLines.slice(0, i + 1).join("\n")) + '<span class="caret">▍</span>';
      i++;
      if (i < heroLines.length) setTimeout(next, 170);
      else setTimeout(() => { i = 0; next(); }, 3200);
    })();
  })();

  // 3D tilt on the code card
  (function tilt() {
    const card = $("#tilt-card"); if (!card || reduceMotion) return;
    const wrap = card.parentElement;
    wrap.addEventListener("mousemove", e => {
      const r = wrap.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width - .5, y = (e.clientY - r.top) / r.height - .5;
      card.style.transform = `perspective(900px) rotateY(${x * 10}deg) rotateX(${-y * 10}deg)`;
    });
    wrap.addEventListener("mouseleave", () => (card.style.transform = ""));
  })();

  // ---------------------------------------------------------- hero: particle network canvas
  (function particles() {
    const cv = $("#hero-canvas"); if (!cv || reduceMotion) return;
    const ctx = cv.getContext("2d");
    let W, H, pts = [], mouse = { x: -999, y: -999 }, running = true;
    function resize() {
      const r = cv.parentElement.getBoundingClientRect();
      W = cv.width = r.width; H = cv.height = r.height;
      const n = Math.min(70, Math.floor(W * H / 16000));
      pts = Array.from({ length: n }, () => ({ x: Math.random() * W, y: Math.random() * H, vx: (Math.random() - .5) * .35, vy: (Math.random() - .5) * .35 }));
    }
    resize(); window.addEventListener("resize", resize);
    cv.parentElement.addEventListener("mousemove", e => { const r = cv.getBoundingClientRect(); mouse.x = e.clientX - r.left; mouse.y = e.clientY - r.top; });
    cv.parentElement.addEventListener("mouseleave", () => (mouse = { x: -999, y: -999 }));
    new IntersectionObserver(en => (running = en[0].isIntersecting)).observe(cv);
    function color() { return getComputedStyle(document.documentElement).getPropertyValue("--indigo").trim() || "#4338ca"; }
    (function frame() {
      requestAnimationFrame(frame);
      if (!running || document.getElementById("home-screen").classList.contains("hidden")) return;
      ctx.clearRect(0, 0, W, H);
      const col = color();
      pts.forEach(p => {
        p.x += p.vx; p.y += p.vy;
        if (p.x < 0 || p.x > W) p.vx *= -1;
        if (p.y < 0 || p.y > H) p.vy *= -1;
        const dx = p.x - mouse.x, dy = p.y - mouse.y, d = Math.hypot(dx, dy);
        if (d < 110) { p.x += dx / d * 1.2; p.y += dy / d * 1.2; }
        ctx.beginPath(); ctx.arc(p.x, p.y, 2, 0, 7); ctx.fillStyle = col; ctx.globalAlpha = .55; ctx.fill();
      });
      for (let i = 0; i < pts.length; i++) for (let j = i + 1; j < pts.length; j++) {
        const d = Math.hypot(pts[i].x - pts[j].x, pts[i].y - pts[j].y);
        if (d < 120) { ctx.globalAlpha = (1 - d / 120) * .25; ctx.strokeStyle = col; ctx.beginPath(); ctx.moveTo(pts[i].x, pts[i].y); ctx.lineTo(pts[j].x, pts[j].y); ctx.stroke(); }
      }
      ctx.globalAlpha = 1;
    })();
  })();

  // ---------------------------------------------------------- counters + scroll reveal
  function countUp(el) {
    if (el.dataset.done) return; el.dataset.done = "1";
    const target = +el.dataset.count || 0, suffix = el.dataset.suffix || "";
    if (reduceMotion || target === 0) { el.textContent = target + suffix; return; }
    const t0 = performance.now(), dur = 1400;
    (function step(t) {
      const p = Math.min(1, (t - t0) / dur), e = 1 - Math.pow(1 - p, 3);
      el.textContent = Math.round(target * e) + suffix;
      if (p < 1) requestAnimationFrame(step);
    })(t0);
  }
  const io = new IntersectionObserver(entries => entries.forEach(en => {
    if (!en.isIntersecting) return;
    en.target.classList.add("in");
    $$("[data-count]", en.target).forEach(countUp);
    if (en.target.matches("[data-count]")) countUp(en.target);
    io.unobserve(en.target);
  }), { threshold: .15 });
  $$(".reveal, .stats-band").forEach((el, i) => { el.style.transitionDelay = (el.classList.contains("fcard") ? (i % 3) * 80 : 0) + "ms"; io.observe(el); });

  // ---------------------------------------------------------- how it works (interactive stepper)
  const steps = [
    { t: "Doctor requests access", i: "🩺", d: "A doctor finds a patient by code and submits an access request with a clinical reason.", code: "POST /api/access-requests\n{ \"doctorId\": 2, \"patientCode\": \"PAT-1001\",\n  \"reason\": \"Follow-up consultation\" }", tag: "Audit: ACCESS_REQUESTED" },
    { t: "Patient decides", i: "🧑", d: "The patient sees the pending request, verifies it, and approves or rejects. Approval creates a Consent record.", code: "PUT /api/access-requests/7/decision\n{ \"decision\": \"APPROVED\" }", tag: "Audit: CONSENT_GRANTED" },
    { t: "Records exchanged", i: "📂", d: "With a granted consent the doctor can read the patient's records and add new ones. Without it the server returns 403.", code: "GET /api/records/patient/1?doctorId=2\n→ 200 OK (consent verified)", tag: "Audit: RECORD_VIEWED" },
    { t: "FHIR bundle generated", i: "🧬", d: "Records are mapped to FHIR resources, assembled into a Bundle, and persisted to the fhir_resources store.", code: "GET /api/fhir/patient/PAT-1001\n→ Bundle { Patient, Condition, ... }", tag: "Audit: FHIR_GENERATED" }
  ];
  (function how() {
    const list = $("#how-steps"), panel = $("#how-panel"); if (!list) return;
    let cur = 0, timer;
    function show(i, userClick) {
      cur = i; const s = steps[i];
      $$("button", list).forEach((b, k) => { b.classList.toggle("active", k === i); b.classList.toggle("done", k < i); });
      panel.innerHTML = `<div class="how-ic">${s.i}</div><h3>Step ${i + 1}: ${s.t}</h3><p>${s.d}</p><pre>${s.code.replace(/</g, "&lt;")}</pre><span class="badge approved">${s.tag}</span>`;
      panel.classList.remove("swap"); void panel.offsetWidth; panel.classList.add("swap");
      if (userClick) { clearInterval(timer); }
    }
    steps.forEach((s, i) => {
      const b = document.createElement("button");
      b.innerHTML = `<span class="num">${i + 1}</span><span>${s.t}</span>`;
      b.onclick = () => show(i, true);
      list.appendChild(b);
    });
    show(0);
    if (!reduceMotion) timer = setInterval(() => show((cur + 1) % steps.length), 5000);
  })();

  // ---------------------------------------------------------- FHIR playground (live JSON generation)
  const FHIR_MAP = {
    CONDITION: "Condition", OBSERVATION: "Observation", MEDICATION: "MedicationRequest",
    ALLERGY: "AllergyIntolerance", IMMUNIZATION: "Immunization", PROCEDURE: "Procedure", REPORT: "DiagnosticReport"
  };
  function buildResource(type, title, detail, pcode) {
    const subject = { reference: "Patient/" + pcode };
    const code = { text: title };
    const note = detail ? [{ text: detail }] : undefined;
    const base = { resourceType: FHIR_MAP[type], id: "demo-" + Math.random().toString(36).slice(2, 8) };
    switch (type) {
      case "CONDITION": return { ...base, clinicalStatus: { coding: [{ code: "active" }] }, code, subject, note };
      case "OBSERVATION": return { ...base, status: "final", code, subject, valueString: detail, effectiveDateTime: new Date().toISOString() };
      case "MEDICATION": return { ...base, status: "active", intent: "order", medicationCodeableConcept: code, subject, note };
      case "ALLERGY": return { ...base, clinicalStatus: { coding: [{ code: "active" }] }, code, patient: subject, note };
      case "IMMUNIZATION": return { ...base, status: "completed", vaccineCode: code, patient: subject, occurrenceDateTime: new Date().toISOString().slice(0, 10), note };
      case "PROCEDURE": return { ...base, status: "completed", code, subject, note };
      default: return { ...base, status: "final", code, subject, conclusion: detail };
    }
  }
  (function playground() {
    const type = $("#pg-type"); if (!type) return;
    const out = $("#pg-json"), label = $("#pg-res-type"), tags = $("#pg-tags");
    const presets = {
      CONDITION: ["Type 2 Diabetes", "Diagnosed during annual checkup"], OBSERVATION: ["Blood pressure", "128/84 mmHg"],
      MEDICATION: ["Metformin 500mg", "Twice daily after meals"], ALLERGY: ["Penicillin", "Rash and mild swelling"],
      IMMUNIZATION: ["Hepatitis B", "Dose 2 of 3"], PROCEDURE: ["Appendectomy", "Laparoscopic, no complications"], REPORT: ["Complete blood count", "All values within range"]
    };
    function render() {
      const res = buildResource(type.value, $("#pg-title").value, $("#pg-detail").value, $("#pg-patient").value || "PAT-0000");
      const json = JSON.stringify(res, null, 2);
      out.innerHTML = highlight(json); label.textContent = "resourceType: " + res.resourceType;
      tags.innerHTML = `<span class="tag">${type.value}</span><span class="tag arrow">→</span><span class="tag amber">${res.resourceType}</span>`;
      out.dataset.raw = json;
    }
    type.onchange = () => { const p = presets[type.value]; $("#pg-title").value = p[0]; $("#pg-detail").value = p[1]; render(); };
    ["pg-title", "pg-detail", "pg-patient"].forEach(id => ($("#" + id).oninput = render));
    $("#pg-copy").onclick = () => navigator.clipboard.writeText(out.dataset.raw).then(() => window.toast && toast("FHIR JSON copied"));
    render();
  })();

  // ---------------------------------------------------------- roles (tabbed showcase)
  const ROLES = {
    patient: { icon: "🧑", title: "Patient dashboard", lead: "You own your data — and decide who sees it.", pts: ["View all medical records and download a PDF report", "Approve or reject incoming doctor requests", "Revoke previously granted consent anytime", "Submit personal profile once; it locks automatically", "Inspect your own FHIR bundle as JSON"], demo: "rahul / patient123" },
    doctor: { icon: "🩺", title: "Doctor dashboard", lead: "Request access, then work with records that are yours to treat.", pts: ["Find patients by code and request access with a reason", "Track status of every request you've made", "Read records only after consent is granted", "Add Condition, Observation, Medication, Procedure & more", "Add new patients and authorize one-time profile unlock"], demo: "dranaya / doctor123" },
    admin: { icon: "⚙️", title: "Admin dashboard", lead: "Full visibility without touching clinical data.", pts: ["Live stats: patients, doctors, requests, consents", "Medical data distribution chart", "Lock or activate any user account", "Complete, searchable audit log", "FHIR resource and event counters"], demo: "admin / admin123" }
  };
  (function roles() {
    const tabs = $$("#role-tabs button"), card = $("#role-card"); if (!card) return;
    function show(k) {
      const r = ROLES[k];
      tabs.forEach(t => t.classList.toggle("active", t.dataset.role === k));
      card.innerHTML = `<div class="role-ic">${r.icon}</div><div><h3>${r.title}</h3><p class="lead-sm">${r.lead}</p>
        <ul>${r.pts.map(p => `<li>${p}</li>`).join("")}</ul>
        <div class="role-demo"><span>Demo login</span><code>${r.demo}</code><button class="primary" data-open-auth="login" data-fill="${r.demo.split(" / ")[0]}|${r.demo.split(" / ")[1]}">Try it →</button></div></div>`;
      card.classList.remove("swap"); void card.offsetWidth; card.classList.add("swap");
      bindAuthButtons(card);
    }
    tabs.forEach(t => (t.onclick = () => show(t.dataset.role)));
    show("patient");
  })();

  // ---------------------------------------------------------- security: DFD stores
  (function dfd() {
    const g = $("#dfd-grid"); if (!g) return;
    [["D1", "Patient Data", "patients"], ["D2", "Medical Records", "medical_records"], ["D3", "Requests & Consents", "access_requests"], ["D4", "Users & Roles", "users"], ["D5", "FHIR Resources", "fhir_resources"], ["D6", "Audit Logs", "audit_logs"]]
      .forEach(([id, n, t]) => g.insertAdjacentHTML("beforeend", `<div class="dfd-cell"><b>${id}</b><span>${n}</span><code>${t}</code></div>`));
  })();

  // ---------------------------------------------------------- FAQ accordion
  (function faq() {
    const list = $("#faq-list"); if (!list) return;
    [["Is this a real clinical system?", "No. FHIRCare is an academic / portfolio project. The FHIR JSON is R5‑shaped for teaching and isn't validated against the full spec."],
     ["Who can see my medical records?", "Only you, and doctors you explicitly approve. Access is checked on the server for every read and write."],
     ["Can I take back access?", "Yes. Patients can revoke any granted consent at any time from Access & Consent."],
     ["How do I try it?", "Use a demo account (patient rahul, doctors dranaya / drvikram, admin) or register your own Patient or Doctor account."],
     ["Where is data stored?", "In MySQL, mapped to the project DFD: patients, records, consents, users, FHIR resources and audit logs."]]
      .forEach(([q, a]) => {
        const d = document.createElement("div"); d.className = "faq-item";
        d.innerHTML = `<button><span>${q}</span><i>＋</i></button><div class="faq-a"><p>${a}</p></div>`;
        const btn = d.firstChild, panel = d.lastChild;
        btn.onclick = () => {
          const open = d.classList.toggle("open");
          panel.style.maxHeight = open ? panel.scrollHeight + "px" : "0";
          btn.lastChild.textContent = open ? "－" : "＋";
        };
        list.appendChild(d);
      });
  })();

  // ---------------------------------------------------------- header: scroll behaviour, progress, scrollspy
  const homeEl = $("#home-screen"), header = $("#home-header"), progress = $("#scroll-progress"), backTop = $("#back-top");
  window.addEventListener("scroll", () => {
    const h = document.documentElement, pct = h.scrollTop / Math.max(1, h.scrollHeight - h.clientHeight) * 100;
    if (progress) progress.style.width = pct + "%";
    if (header) header.classList.toggle("scrolled", h.scrollTop > 20);
    backTop.classList.toggle("hidden", h.scrollTop < 500);
    const ids = ["features", "how", "fhir", "roles", "security", "faq"]; let active = "";
    ids.forEach(id => { const el = document.getElementById(id); if (el && el.getBoundingClientRect().top < 160) active = id; });
    $$("#home-nav a").forEach(a => a.classList.toggle("active", a.dataset.scroll === active));
  }, { passive: true });
  backTop.onclick = () => window.scrollTo({ top: 0, behavior: "smooth" });
  $("#menu-toggle").onclick = () => $("#home-nav").classList.toggle("open");

  function scrollToSection(id) {
    const el = id === "top" ? document.body : document.getElementById(id);
    if (!el) return;
    window.scrollTo({ top: id === "top" ? 0 : el.getBoundingClientRect().top + scrollY - 76, behavior: "smooth" });
    $("#home-nav").classList.remove("open");
  }
  $$("#home-nav a, .site-header .brand").forEach(a => a.onclick = e => { e.preventDefault(); scrollToSection(a.dataset.scroll); });

  // ---------------------------------------------------------- auth buttons
  function bindAuthButtons(root = document) {
    $$("[data-open-auth]", root).forEach(b => b.onclick = () => {
      window.Home.openAuth(b.dataset.openAuth, b.dataset.fill);
    });
  }
  bindAuthButtons();

  // ---------------------------------------------------------- public API for app.js
  window.Home = {
    countUp(el) { el.dataset.done = ""; countUp(el); },
    setUser(user) {
      const a = $$(".site-actions [data-open-auth]");
      const [signin, start] = a;
      if (user) {
        signin.textContent = user.fullName.split(" ")[0];
        signin.onclick = () => window.showApp && showApp();
        start.textContent = "Open dashboard →";
        start.onclick = () => window.showApp && showApp();
      } else {
        signin.textContent = "Sign in"; start.textContent = "Get started";
        signin.onclick = () => Home.openAuth("login"); start.onclick = () => Home.openAuth("register");
      }
      $$(".hero-cta [data-open-auth], .cta-band [data-open-auth]").forEach(b => {
        if (user) { b.onclick = () => window.showApp && showApp(); }
        else b.onclick = () => Home.openAuth(b.dataset.openAuth);
      });
    },
    show() {
      homeEl.classList.remove("hidden");
      $("#auth-screen").classList.add("hidden");
      $("#app-shell").classList.add("hidden");
      $("#site-footer").classList.remove("hidden");
      window.scrollTo(0, 0); pingApi();
    },
    hide() { homeEl.classList.add("hidden"); },
    goHome(section) {
      // from the app shell: leave to the public site (user stays signed in)
      $("#app-shell").classList.add("hidden"); $("#auth-screen").classList.add("hidden");
      homeEl.classList.remove("hidden");
      $("#site-footer").classList.remove("hidden");
      pingApi();
      setTimeout(() => section ? scrollToSection(section) : window.scrollTo(0, 0), 30);
    },
    openAuth(tab, fill) {
      homeEl.classList.add("hidden"); $("#app-shell").classList.add("hidden");
      $("#auth-screen").classList.remove("hidden");
      $("#site-footer").classList.add("hidden");
      const t = $(`.tab[data-tab="${tab || "login"}"]`); if (t) t.click();
      if (fill) { const [u, p] = fill.split("|"); $("#login-username").value = u; $("#login-password").value = p; }
      window.scrollTo(0, 0);
    }
  };
  $("#auth-back").onclick = () => window.Home.show();
})();
