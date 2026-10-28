// ==========================================================================
// CampusMesh Web Dashboard Application Engine
// Delay-Tolerant Networking (DTN) P2P Cross-Platform Control Center
// Dynamic Online-Only Mesh Topology Radar
// ==========================================================================

const API_BASE = window.location.origin;

const state = {
    activeNodeId: localStorage.getItem("campusmesh_active_node") || null,
    serverLocalPeerId: "LAPTOP_WIN",
    localPeer: { id: "LAPTOP_WIN", ip: "127.0.0.1", port: 5001 },
    strategy: "ADAPTIVE",
    weights: { wP: 0.40, wT: 0.40, wB: 0.20 },
    activeContacts: new Set(),
    peers: [],
    buffer: [],
    evaluation: [],
    events: [],
    selectedNode: null,
    hoveredNode: null,
    animPackets: [],
    theme: localStorage.getItem("campusmesh_theme") || "dark",
    apiToken: null,
    isDemoActive: false
};

// Dynamic Topology Nodes & Links (Populated strictly from online/discovered peers)
let nodes = {};
let links = [];

// --------------------------------------------------------------------------
// Initialization
// --------------------------------------------------------------------------
document.addEventListener("DOMContentLoaded", () => {
    initTheme();
    initCanvas();
    initEventListeners();
    updateDynamicTopology();
    startPollingLoop();
});

// --------------------------------------------------------------------------
// Theme Management (Dark & Light)
// --------------------------------------------------------------------------
function initTheme() {
    applyTheme(state.theme);

    const toggleBtn = document.getElementById("themeToggleBtn");
    if (toggleBtn) {
        toggleBtn.addEventListener("click", () => {
            const newTheme = document.body.classList.contains("dark-theme") ? "light" : "dark";
            applyTheme(newTheme);
            state.theme = newTheme;
            localStorage.setItem("campusmesh_theme", state.theme);
        });
    }
}

function applyTheme(theme) {
    const icon = document.getElementById("themeIcon");
    if (theme === "light") {
        document.body.classList.remove("dark-theme");
        document.body.classList.add("light-theme");
        if (icon) {
            icon.innerHTML = `<path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>`;
        }
    } else {
        document.body.classList.remove("light-theme");
        document.body.classList.add("dark-theme");
        if (icon) {
            icon.innerHTML = `<circle cx="12" cy="12" r="5"></circle><path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"></path>`;
        }
    }
}

// --------------------------------------------------------------------------
// Active Node Identity Management (Mobile vs Laptop)
// --------------------------------------------------------------------------
window.promptChangeNodeId = function() {
    const current = state.myNodeId || "MY_NODE";
    const entered = prompt("Enter your local mesh Call-sign / Device Node ID:\n(Letters, numbers, underscores only, max 20 chars)", current);
    if (entered) {
        const cleaned = entered.trim().toUpperCase().replace(/[^A-Z0-9_.-]/g, "_").substring(0, 20);
        if (cleaned) {
            localStorage.setItem("campusmesh_my_node_id", cleaned);
            applyMyNodeId(cleaned);
            fetchData();
            showToast(`Node identity set to: ${cleaned}`, "success");
        }
    }
};

function detectInitialNode(resStatus) {
    const isLocalhost = window.location.hostname === "localhost" || 
                        window.location.hostname === "127.0.0.1" || 
                        window.location.hostname === "0.0.0.0";
    const isMobileUA = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent);

    const stored = localStorage.getItem("campusmesh_my_node_id");
    // Clear any stale demo default 'PHONE_MOBILE' if on localhost/desktop
    if (stored && (!isLocalhost || stored !== "PHONE_MOBILE")) {
        applyMyNodeId(stored);
        return;
    }

    let chosen;
    if (isLocalhost || !isMobileUA) {
        // Desktop / Host machine
        chosen = (resStatus && resStatus.localPeerId) ? resStatus.localPeerId : "LAPTOP_WIN";
        localStorage.setItem("campusmesh_my_node_id", chosen);
    } else {
        // Mobile device accessing over Wi-Fi: generate distinct unique node call-sign
        const rand = Math.floor(100 + Math.random() * 900);
        let prefix = "PHONE";
        if (/iPhone|iPad/i.test(navigator.userAgent)) prefix = "IPHONE";
        else if (/Android/i.test(navigator.userAgent)) prefix = "PHONE_AND";
        chosen = `${prefix}_${rand}`;
        localStorage.setItem("campusmesh_my_node_id", chosen);
    }
    applyMyNodeId(chosen);
}

function applyMyNodeId(nodeId) {
    state.myNodeId = nodeId;
    state.activeNodeId = nodeId;

    const isLocalhost = window.location.hostname === "localhost" || 
                        window.location.hostname === "127.0.0.1";
    const isMobileUA = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent);

    const badge = document.getElementById("myNodeIdBadge");
    if (badge) {
        let icon = (!isLocalhost && (isMobileUA || /MOBILE|PHONE/i.test(nodeId))) ? "📱" : "💻";
        badge.textContent = `${icon} ${nodeId}`;
    }

    const srcInput = document.getElementById("composeSource");
    if (srcInput) {
        srcInput.value = nodeId;
    }

    const destInput = document.getElementById("composeDest");
    if (destInput && (!destInput.value || destInput.value === nodeId)) {
        destInput.value = "BROADCAST";
    }

    updateDynamicTopology();
}

// --------------------------------------------------------------------------
// Dynamic Mesh Radar Topology (Strictly Online Peers Only)
// --------------------------------------------------------------------------
function updateDynamicTopology() {
    if (state.isDemoActive) return;
    const localId = state.myNodeId || state.activeNodeId || state.localPeer.id || "LAPTOP_WIN";
    const newNodes = {};
    const newLinks = [];

    // 1. Center Local Operating Node (This Device)
    let localPlatform = "LOCAL";
    let localLabel = `This Device (${localId})`;
    if (localId.includes("DOCKER")) { localPlatform = "WINDOWS"; }
    else if (localId.includes("WIN")) { localPlatform = "WINDOWS"; }
    else if (localId.includes("UBU") || localId.includes("LINUX")) { localPlatform = "UBUNTU"; }
    else if (localId.includes("AND")) { localPlatform = "ANDROID"; }
    else if (localId.includes("IOS") || localId.includes("IPHONE")) { localPlatform = "IOS"; }
    else if (localId.includes("MOBILE") || localId.includes("PHONE")) { localPlatform = "MOBILE"; }

    newNodes[localId] = {
        id: localId,
        x: 300,
        y: 190,
        label: localLabel,
        platform: localPlatform,
        isLocal: true,
        inContact: false
    };

    // 2. Filter ONLY strictly online (non-expired) peers that are not the local peer
    const onlinePeers = (state.peers || []).filter(p => !p.isExpired && p.peerId !== localId);

    // Sort peers alphabetically by peerId so radial positions remain visually stable
    const sortedPeers = [...onlinePeers].sort((a, b) => a.peerId.localeCompare(b.peerId));

    const N = sortedPeers.length;
    const cx = 300;
    const cy = 190;
    const radius = N <= 3 ? 130 : (N <= 6 ? 140 : 155);

    sortedPeers.forEach((p, idx) => {
        const angle = -Math.PI / 2 + (2 * Math.PI * idx) / N;
        const x = Math.round(cx + radius * Math.cos(angle));
        const y = Math.round(cy + radius * Math.sin(angle));

        const inContact = p.inContact || state.activeContacts.has(p.peerId);
        const transports = p.transports || ["WIFI"];

        newNodes[p.peerId] = {
            id: p.peerId,
            x: x,
            y: y,
            label: p.displayName || p.peerId,
            platform: p.platform || "PEER",
            isLocal: false,
            inContact: inContact,
            transports: transports,
            lastSeenSecondsAgo: p.lastSeenSecondsAgo || 0
        };

        // Create dynamic link between local node and online peer
        const primaryTransport = transports.includes("WIFI") ? "WIFI" : "BLE";
        newLinks.push({
            from: localId,
            to: p.peerId,
            type: primaryTransport,
            inContact: inContact
        });
    });

    nodes = newNodes;
    links = newLinks;

    // Update online badge in canvas header
    const badge = document.getElementById("radarOnlineBadge");
    if (badge) {
        badge.textContent = N > 0 ? `${N} Online Peer${N > 1 ? 's' : ''}` : "Searching for Peers";
    }

    renderQuickTargetPills(sortedPeers);
}

function renderQuickTargetPills(onlinePeers) {
    const container = document.getElementById("quickTargetPills");
    if (!container) return;

    let html = "";
    if (onlinePeers && onlinePeers.length > 0) {
        html += onlinePeers.map(p => {
            let icon = "💻";
            if (p.platform === "ANDROID" || p.platform === "IOS" || p.platform === "MOBILE") {
                icon = "📱";
            }
            return `<button type="button" class="target-pill" onclick="setDestination('${escapeHtml(p.peerId)}')">${icon} ${escapeHtml(p.peerId)}</button>`;
        }).join(" ");
    }
    html += `<button type="button" class="target-pill" onclick="setDestination('BROADCAST')">📡 BROADCAST</button>`;
    container.innerHTML = html;
}

function initEventListeners() {
    // Strategy Selector
    const stratSelect = document.getElementById("strategySelect");
    if (stratSelect) {
        stratSelect.addEventListener("change", (e) => {
            const strat = e.target.value;
            const panel = document.getElementById("adaptiveWeightsPanel");
            if (panel) {
                panel.style.display = (strat === "ADAPTIVE") ? "block" : "none";
            }
            updateStrategyOnServer(strat, state.weights.wP, state.weights.wT, state.weights.wB);
        });
    }

    // Weight Sliders
    const sWp = document.getElementById("sliderWp");
    const sWt = document.getElementById("sliderWt");
    const sWb = document.getElementById("sliderWb");

    const onWeightChange = () => {
        if (!sWp || !sWt || !sWb) return;
        let wp = parseFloat(sWp.value);
        let wt = parseFloat(sWt.value);
        let wb = parseFloat(sWb.value);

        // Normalize sum to 1.0
        const sum = wp + wt + wb;
        if (sum > 0) {
            wp = Math.round((wp / sum) * 100) / 100;
            wt = Math.round((wt / sum) * 100) / 100;
            wb = Math.round((1.0 - wp - wt) * 100) / 100;
        }

        updateWeightDisplays(wp, wt, wb);
        state.weights = { wP: wp, wT: wt, wB: wb };
        updateStrategyOnServer(state.strategy, wp, wt, wb);
    };

    if (sWp) sWp.addEventListener("input", onWeightChange);
    if (sWt) sWt.addEventListener("input", onWeightChange);
    if (sWb) sWb.addEventListener("input", onWeightChange);

    // TTL slider label update
    const composeTtl = document.getElementById("composeTtl");
    if (composeTtl) {
        composeTtl.addEventListener("input", (e) => {
            const sec = parseInt(e.target.value);
            const min = Math.round(sec / 60);
            const hours = (sec / 3600).toFixed(1);
            const labelEl = document.getElementById("composeTtlVal");
            if (labelEl) {
                labelEl.textContent = sec >= 3600 ? `${hours} hrs` : `${min} min`;
            }
        });
    }

    // Payload byte counter & shortcuts
    const payloadInput = document.getElementById("composePayload");
    if (payloadInput) {
        payloadInput.addEventListener("input", updatePayloadByteCounter);
        payloadInput.addEventListener("keydown", (e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
                e.preventDefault();
                submitMessage();
            }
        });
        updatePayloadByteCounter();
    }

    // Compose Form Submit
    const form = document.getElementById("composeForm");
    if (form) {
        form.addEventListener("submit", (e) => {
            e.preventDefault();
            submitMessage();
        });
    }

    // Purge Expired Button
    const btnPurge = document.getElementById("btnPurgeExpired");
    if (btnPurge) {
        btnPurge.addEventListener("click", () => {
            const beforeCount = state.buffer.length;
            state.buffer = state.buffer.filter(m => !m.isExpired);
            const purgedCount = beforeCount - state.buffer.length;
            renderBuffer();
            showToast(purgedCount > 0 ? `Purged ${purgedCount} expired message(s).` : "No expired messages to purge.", "info");
        });
    }

    // Scan Radios & Sync Mesh Button
    const btnScan = document.getElementById("btnScanBle");
    if (btnScan) {
        btnScan.addEventListener("click", () => {
            showToast("Scanning Wi-Fi subnets & BLE 0000FE60 beacons...", "info");
            fetchData();
        });
    }
}

function updateWeightDisplays(wp, wt, wb) {
    const elWp = document.getElementById("valWp");
    const elWt = document.getElementById("valWt");
    const elWb = document.getElementById("valWb");
    if (elWp) elWp.textContent = wp.toFixed(2);
    if (elWt) elWt.textContent = wt.toFixed(2);
    if (elWb) elWb.textContent = wb.toFixed(2);
}

function updatePayloadByteCounter() {
    const payloadEl = document.getElementById("composePayload");
    const counterEl = document.getElementById("payloadBytesCounter");
    if (payloadEl && counterEl) {
        const bytes = new TextEncoder().encode(payloadEl.value).length;
        counterEl.textContent = `${bytes.toLocaleString()} / 65,536 bytes`;
        if (bytes > 65536) {
            counterEl.style.color = "var(--accent-red)";
        } else {
            counterEl.style.color = "var(--text-muted)";
        }
    }
}

// --------------------------------------------------------------------------
// Quick Preset & Template Handlers
// --------------------------------------------------------------------------
window.applyPresetWeights = function(wp, wt, wb) {
    const sWp = document.getElementById("sliderWp");
    const sWt = document.getElementById("sliderWt");
    const sWb = document.getElementById("sliderWb");

    if (sWp) sWp.value = wp;
    if (sWt) sWt.value = wt;
    if (sWb) sWb.value = wb;

    updateWeightDisplays(wp, wt, wb);
    state.weights = { wP: wp, wT: wt, wB: wb };
    updateStrategyOnServer("ADAPTIVE", wp, wt, wb);
    showToast(`Preset applied: wP=${wp}, wT=${wt}, wB=${wb}`);
};

window.swapNodes = function() {
    const srcInput = document.getElementById("composeSource");
    const destInput = document.getElementById("composeDest");
    if (srcInput && destInput) {
        const tmp = srcInput.value;
        srcInput.value = destInput.value;
        destInput.value = tmp;
        showToast(`Swapped: ${srcInput.value} ➔ ${destInput.value}`);
    }
};

window.setDestination = function(destId) {
    const destInput = document.getElementById("composeDest");
    if (destInput) {
        destInput.value = destId;
        showToast(`Destination target set to ${destId}`);
    }
};

window.applyTemplate = function(templateKey) {
    const payloadInput = document.getElementById("composePayload");
    const prioritySelect = document.getElementById("composePriority");
    const ttlSlider = document.getElementById("composeTtl");
    const tokensInput = document.getElementById("composeTokens");

    if (templateKey === "CAMPUS_ALERT") {
        if (payloadInput) payloadInput.value = "CRITICAL ALERT: Severe storm approaching campus. Power may be disconnected. Evacuate to Main Pavilion Hall.";
        if (prioritySelect) prioritySelect.value = "HIGH";
        if (ttlSlider) { ttlSlider.value = 3600; ttlSlider.dispatchEvent(new Event("input")); }
        if (tokensInput) tokensInput.value = 8;
        showToast("Loaded Severe Weather Alert Template (HIGH priority, L=8)");
    } else if (templateKey === "LAB_NOTICE") {
        if (payloadInput) payloadInput.value = "NOTICE: Science block lab workstations rebooting at 18:30 for system kernel patch. Save active data.";
        if (prioritySelect) prioritySelect.value = "MEDIUM";
        if (ttlSlider) { ttlSlider.value = 7200; ttlSlider.dispatchEvent(new Event("input")); }
        if (tokensInput) tokensInput.value = 4;
        showToast("Loaded Lab Notice Template (MEDIUM priority, L=4)");
    } else if (templateKey === "STUDY_GROUP") {
        if (payloadInput) payloadInput.value = "CS401 Study Group meeting in Library 2nd floor study carrels. Bring your laptops!";
        if (prioritySelect) prioritySelect.value = "LOW";
        if (ttlSlider) { ttlSlider.value = 5400; ttlSlider.dispatchEvent(new Event("input")); }
        if (tokensInput) tokensInput.value = 2;
        showToast("Loaded Study Group Template (LOW priority, L=2)");
    } else if (templateKey === "LOST_KEYS") {
        if (payloadInput) payloadInput.value = "FOUND: Set of Honda car keys found near sports arena north entrance. Deposited at reception security desk.";
        if (prioritySelect) prioritySelect.value = "LOW";
        if (ttlSlider) { ttlSlider.value = 14400; ttlSlider.dispatchEvent(new Event("input")); }
        if (tokensInput) tokensInput.value = 3;
        showToast("Loaded Lost & Found Template (LOW priority, L=3)");
    }

    updatePayloadByteCounter();
};

window.openMobileModal = function() {
    const modal = document.getElementById("mobileConnectModal");
    if (modal) {
        modal.style.display = "flex";
        const port = window.location.port || 8080;
        let host = state.serverLanIp;
        // Docker containers report 172.x.x.x (bridge network) which is unreachable from mobile.
        // In that case, use the hostname the browser is already connected to (the host's real LAN IP).
        const isDockerBridge = host && host.startsWith("172.");
        if (!host || host === "127.0.0.1" || host === "localhost" || isDockerBridge) {
            const browserHost = window.location.hostname;
            if (browserHost && browserHost !== "127.0.0.1" && browserHost !== "localhost" && browserHost !== "0.0.0.0") {
                host = browserHost;
            } else {
                host = "YOUR_LAPTOP_IP";
            }
        }
        const input = document.getElementById("mobileUrlInput");
        if (input) {
            const tokenHash = state.apiToken ? `/#token=${encodeURIComponent(state.apiToken)}` : "";
            input.value = `http://${host}:${port}${tokenHash}`;
        }
    }
};

window.closeMobileModal = function() {
    const modal = document.getElementById("mobileConnectModal");
    if (modal) {
        modal.style.display = "none";
    }
};

window.copyMobileUrl = function() {
    const input = document.getElementById("mobileUrlInput");
    if (input) {
        input.select();
        navigator.clipboard.writeText(input.value).then(() => {
            showToast("Copied mobile URL to clipboard!", "success");
        }).catch(() => {
            showToast("URL: " + input.value);
        });
    }
};

document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
        closeMobileModal();
    }
});

// --------------------------------------------------------------------------
// Real-Time Polling Loop
// --------------------------------------------------------------------------
function startPollingLoop() {
    fetchData();
    setInterval(fetchData, 1500);
}

async function fetchData() {
    try {
        const pollHeaders = {};
        if (state.myNodeId) pollHeaders["X-Node-ID"] = state.myNodeId;

        const [resStatus, resPeers, resBuf, resEval, resEvents] = await Promise.all([
            fetch(`${API_BASE}/api/status`, { headers: pollHeaders }).then(r => r.json()).catch(() => null),
            fetch(`${API_BASE}/api/peers`).then(r => r.json()).catch(() => []),
            fetch(`${API_BASE}/api/buffer`).then(r => r.json()).catch(() => []),
            fetch(`${API_BASE}/api/evaluation`).then(r => r.json()).catch(() => []),
            fetch(`${API_BASE}/api/events`).then(r => r.json()).catch(() => [])
        ]);

        if (resStatus) {
            if (resStatus.apiToken) {
                state.apiToken = resStatus.apiToken;
            } else if (!state.apiToken) {
                // Remote device (phone): token comes from the link printed in the console, e.g. http://IP:8080/#token=...
                const t = new URLSearchParams((location.hash || "").replace(/^#/, "")).get("token");
                if (t) state.apiToken = t;
            }
            state.serverLocalPeerId = resStatus.localPeerId || "LAPTOP_WIN";
            state.localPeer.id = resStatus.localPeerId || "LAPTOP_WIN";
            state.strategy = resStatus.strategy || "ADAPTIVE";
            if (resStatus.weights) state.weights = resStatus.weights;
            state.activeContacts = new Set(resStatus.activeContacts || []);

            // Initialize or sync this device's real node identity
            if (!state.myNodeId) {
                detectInitialNode(resStatus);
            } else {
                applyMyNodeId(state.myNodeId);
            }

            const navStrat = document.getElementById("navStrategy");
            if (navStrat) navStrat.textContent = state.strategy;

            if (resStatus.serverLanIp) {
                state.serverLanIp = resStatus.serverLanIp;
                const port = resStatus.serverPort || window.location.port || 8080;
                const mobileUrlInput = document.getElementById("mobileUrlInput");
                if (mobileUrlInput && !mobileUrlInput.dataset.manual) {
                    let displayIp = resStatus.serverLanIp;
                    // Docker containers report 172.x.x.x bridge IPs; use the browser's host instead
                    if (displayIp.startsWith("172.") || displayIp === "127.0.0.1") {
                        const bh = window.location.hostname;
                        if (bh && bh !== "127.0.0.1" && bh !== "localhost" && bh !== "0.0.0.0") {
                            displayIp = bh;
                        }
                    }
                    const tokenHash = state.apiToken ? `/#token=${encodeURIComponent(state.apiToken)}` : "";
                    mobileUrlInput.value = `http://${displayIp}:${port}${tokenHash}`;
                }
            }

            const stratSelect = document.getElementById("strategySelect");
            if (stratSelect && stratSelect.value !== state.strategy) {
                stratSelect.value = state.strategy;
            }

            const navBuf = document.getElementById("navBufferPressure");
            if (navBuf) navBuf.textContent = (parseFloat(resStatus.bufferPressure || 0) * 100).toFixed(1) + "%";

            // Update pressure bar
            const pct = Math.min(100, Math.round(parseFloat(resStatus.bufferPressure || 0) * 100));
            const bar = document.getElementById("bufferPressureBar");
            if (bar) {
                bar.style.width = pct + "%";
                if (pct > 80) {
                    bar.style.background = "linear-gradient(90deg, var(--accent-amber), var(--accent-red))";
                } else if (pct > 50) {
                    bar.style.background = "linear-gradient(90deg, var(--accent-cyan), var(--accent-amber))";
                } else {
                    bar.style.background = "linear-gradient(90deg, var(--accent-cyan), var(--accent-purple))";
                }
            }

            const bCount = document.getElementById("bufCount");
            if (bCount) bCount.textContent = resStatus.bufferSize || 0;
            const bBytes = document.getElementById("bufBytes");
            if (bBytes) bBytes.textContent = Math.round((resStatus.bufferUsedBytes || 0) / 1024) + " KB";

            // Ribbon items
            const rCount = document.getElementById("ribbonMsgCount");
            if (rCount) rCount.textContent = `${resStatus.bufferSize || 0} msgs`;
            const rBytes = document.getElementById("ribbonBufBytes");
            if (rBytes) rBytes.textContent = `${Math.round((resStatus.bufferUsedBytes || 0) / 1024)} KB`;
        }

        if (resPeers) {
            state.peers = resPeers;
            updateDynamicTopology();
            renderPeerChips();

            const onlineCount = state.peers.filter(p => !p.isExpired).length;
            const rPeers = document.getElementById("ribbonPeersCount");
            if (rPeers) rPeers.textContent = `${onlineCount} Online`;
        }

        if (resBuf) {
            state.buffer = resBuf;
            renderBuffer();
            if (resBuf.length > 0) {
                renderBleInspector(resBuf[0]);
            }
        }

        if (resEval) {
            state.evaluation = resEval;
            renderDecisionTable();
        }

        if (resEvents) {
            state.events = resEvents;
            renderEventsLog();
        }

    } catch (err) {
        console.warn("Polling error:", err);
    }
}

// --------------------------------------------------------------------------
// Peer Chips Rendering (Only Online Discovered Peers)
// --------------------------------------------------------------------------
function renderPeerChips() {
    const list = document.getElementById("peerChipsList");
    if (!list) return;

    const localId = state.activeNodeId || state.localPeer.id || "LAPTOP_WIN";
    const onlinePeers = (state.peers || []).filter(p => !p.isExpired && p.peerId !== localId);

    if (onlinePeers.length === 0) {
        list.innerHTML = `<span class="text-muted" style="font-size:0.75rem;">No remote peers currently online. Broadcasting Wi-Fi mDNS beacon & listening on BLE 0000FE60...</span>`;
        return;
    }

    list.innerHTML = onlinePeers.map(p => {
        const inContact = state.activeContacts.has(p.peerId) || p.inContact;
        const txBadge = (p.transports || []).map(t => {
            return `<span class="badge ${t === 'WIFI' ? 'badge-medium' : 'badge-accent'}">${t}</span>`;
        }).join(" ");

        let icon = "💻";
        if (p.platform === "ANDROID" || p.platform === "IOS" || p.platform === "MOBILE") {
            icon = "📱";
        }

        return `
            <div class="peer-chip ${inContact ? 'in-contact' : ''}" onclick="selectPeer('${escapeHtml(p.peerId)}')" title="Click to set ${escapeHtml(p.peerId)} as Destination">
                <span>${icon} <strong>${escapeHtml(p.peerId)}</strong></span>
                <span class="text-muted">(${escapeHtml(p.platform)})</span>
                ${txBadge}
            </div>
        `;
    }).join("");
}

window.selectPeer = function(peerId) {
    if (peerId === state.activeNodeId) {
        showToast(`${peerId} is currently this device's active operating node.`, "info");
        return;
    }
    const destInput = document.getElementById("composeDest");
    if (destInput) {
        destInput.value = peerId;
    }
    showToast(`Target set to ${peerId}`);
};

// Buffer Filter State: "all" | "inbox" | "sent"
state.bufferFilter = "all";

window.setBufferFilter = function(filter) {
    state.bufferFilter = filter;
    document.querySelectorAll(".buffer-tab").forEach(tab => {
        tab.classList.toggle("active", tab.id === `tabBuf${filter.charAt(0).toUpperCase() + filter.slice(1)}`);
    });
    renderBuffer();
};

function renderBuffer() {
    const list = document.getElementById("bufferList");
    if (!list) return;

    const allMsgs = state.buffer || [];
    const myId = state.myNodeId || state.activeNodeId || "";

    const inboxMsgs = allMsgs.filter(m => m.destinationId === "BROADCAST" || m.destinationId === myId);
    const sentMsgs = allMsgs.filter(m => m.sourceId === myId);

    const cAll = document.getElementById("bufCountAll");
    const cInbox = document.getElementById("bufCountInbox");
    const cSent = document.getElementById("bufCountSent");
    if (cAll) cAll.textContent = allMsgs.length;
    if (cInbox) cInbox.textContent = inboxMsgs.length;
    if (cSent) cSent.textContent = sentMsgs.length;

    let filtered = allMsgs;
    if (state.bufferFilter === "inbox") filtered = inboxMsgs;
    else if (state.bufferFilter === "sent") filtered = sentMsgs;

    if (filtered.length === 0) {
        let emptyLabel = "No DTN messages currently in local store.";
        if (state.bufferFilter === "inbox") emptyLabel = "No incoming messages addressed to this device.";
        else if (state.bufferFilter === "sent") emptyLabel = "No messages sent from this device yet.";
        list.innerHTML = `
            <div class="empty-state">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>
                <span>${emptyLabel}</span>
            </div>`;
        return;
    }

    list.innerHTML = filtered.map(m => {
        const isMine = m.sourceId === myId;
        const isForMe = m.destinationId === myId;
        const isBroadcast = m.destinationId === "BROADCAST";
        const pClass = m.priority === "HIGH" ? "badge-high" : (m.priority === "MEDIUM" ? "badge-medium" : "badge-low");
        const remainingSec = Math.max(0, m.remainingTTL || 0);
        const ttlMin = Math.round(remainingSec / 60);

        let roleBadge = "";
        if (isMine) roleBadge = `<span class="badge" style="background: rgba(56, 189, 248, 0.2); color: var(--accent-cyan); font-size: 0.68rem;">📤 Outbox (Sent)</span>`;
        else if (isForMe) roleBadge = `<span class="badge" style="background: rgba(74, 222, 128, 0.2); color: var(--accent-green); font-size: 0.68rem;">📥 Inbox (For You)</span>`;
        else if (isBroadcast) roleBadge = `<span class="badge" style="background: rgba(245, 158, 11, 0.2); color: var(--accent-amber); font-size: 0.68rem;">📡 Broadcast</span>`;
        else roleBadge = `<span class="badge" style="background: rgba(168, 85, 247, 0.2); color: var(--accent-purple); font-size: 0.68rem;">📦 Mesh Relay</span>`;

        let ttlColor = "var(--accent-green)";
        if (remainingSec < 300) {
            ttlColor = "var(--accent-red)";
        } else if (remainingSec < 900) {
            ttlColor = "var(--accent-amber)";
        }

        return `
            <div class="message-card" onclick='inspectMessage(${JSON.stringify(m)})' title="Click to inspect Bluetooth LE packets">
                <div class="message-header">
                    <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
                        <span class="msg-id">${escapeHtml(m.messageId)}</span>
                        <span class="msg-path">${escapeHtml(m.sourceId)} ➔ ${escapeHtml(m.destinationId)}</span>
                        ${roleBadge}
                    </div>
                    <span class="badge ${pClass}">${escapeHtml(m.priority)}</span>
                </div>
                <div class="msg-payload">${escapeHtml(m.payload)}</div>
                <div class="msg-footer">
                    <span>Tokens: <strong class="text-accent">${m.copyTokens}</strong></span>
                    <div style="display:flex; align-items:center; gap:8px;">
                        <span>TTL: <strong>${ttlMin}m</strong></span>
                        <div class="ttl-bar-wrapper">
                            <div class="ttl-bar" style="width: ${Math.min(100, Math.max(5, (remainingSec / 3600) * 100))}%; background: ${ttlColor};"></div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }).join("");
}

// --------------------------------------------------------------------------
// Decision Matrix Table Rendering
// --------------------------------------------------------------------------
function renderDecisionTable() {
    const body = document.getElementById("decisionTableBody");
    if (!body) return;

    if (!state.evaluation || state.evaluation.length === 0) {
        body.innerHTML = `<tr><td colspan="7" class="text-center text-muted" style="padding: 24px;">Awaiting candidate messages in buffer to rank...</td></tr>`;
        return;
    }

    body.innerHTML = state.evaluation.map(sc => {
        const pBadge = sc.priority === "HIGH" ? "badge-high" : (sc.priority === "MEDIUM" ? "badge-medium" : "badge-low");
        const selBadge = sc.selected
            ? `<span class="badge badge-success"><svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"/></svg> FORWARD</span>`
            : `<span class="badge badge-low">HOLD</span>`;

        return `
            <tr class="${sc.selected ? 'selected-row' : ''}">
                <td><strong>#${sc.rank}</strong></td>
                <td><span class="font-mono font-bold">${escapeHtml(sc.messageId)}</span></td>
                <td><span class="badge ${pBadge}">${sc.priority} (${sc.p})</span></td>
                <td><span class="font-mono">${sc.t}</span></td>
                <td><span class="font-mono">${sc.b}</span></td>
                <td><strong class="text-accent font-mono" style="font-size:0.85rem;">${sc.utility}</strong></td>
                <td>${selBadge}</td>
            </tr>
        `;
    }).join("");
}

// --------------------------------------------------------------------------
// BLE Packet Inspector
// --------------------------------------------------------------------------
function renderBleInspector(msg) {
    if (!msg) return;
    const view = document.getElementById("bleChunksView");
    if (!view) return;

    const jsonStr = JSON.stringify(msg);
    const bytesCount = new TextEncoder().encode(jsonStr).length;

    // Header is 13 bytes, chunk payload is 7 bytes at MTU 20
    const maxPayload = 7;
    const totalChunks = Math.ceil(bytesCount / maxPayload);
    const crc = crc32(jsonStr).toString(16).toUpperCase().padStart(8, "0");

    let html = `<div style="margin-bottom:8px; display:flex; justify-content:space-between; flex-wrap:wrap; gap:6px;">
        <span><strong>Envelope for ${escapeHtml(msg.messageId)}</strong> (${bytesCount} bytes)</span>
        <span>Chunks: <strong class="text-accent font-mono">${totalChunks}</strong> | CRC32: <strong class="text-success font-mono">0x${crc}</strong></span>
    </div>`;

    html += `<div style="display:flex; flex-direction:column; gap:4px;">`;
    for (let i = 0; i < Math.min(4, totalChunks); i++) {
        html += `<div style="font-size:0.71rem;">• Chunk [${i + 1}/${totalChunks}]: Header=13B [0x43 0x4D 0x02 Tx=0001 #${i + 1} 0x${crc}] + Payload=${Math.min(maxPayload, bytesCount - (i * maxPayload))}B</div>`;
    }
    if (totalChunks > 4) {
        html += `<div class="text-muted" style="margin-top:2px;">... and ${totalChunks - 4} more MTU packets awaiting GATT transfer.</div>`;
    }
    html += `</div>`;
    view.innerHTML = html;
}

window.inspectMessage = function(msg) {
    renderBleInspector(msg);
    showToast(`Inspecting Bluetooth LE frames for ${msg.messageId}`);
};

// --------------------------------------------------------------------------
// SQLite Event Logs
// --------------------------------------------------------------------------
function renderEventsLog() {
    const stream = document.getElementById("logsStream");
    if (!stream) return;

    if (!state.events || state.events.length === 0) {
        stream.innerHTML = `<div>No events logged yet.</div>`;
        return;
    }

    stream.innerHTML = state.events.slice(0, 20).map(e => {
        const time = (e.timestamp || "").split("T")[1] || e.timestamp;
        let eventColor = "var(--accent-cyan)";
        if (e.eventType.includes("DELIVERED") || e.eventType.includes("SUCCESS")) {
            eventColor = "var(--accent-green)";
        } else if (e.eventType.includes("ERROR") || e.eventType.includes("DROPPED") || e.eventType.includes("EXPIRED")) {
            eventColor = "var(--accent-red)";
        } else if (e.eventType.includes("CONTACT")) {
            eventColor = "var(--accent-purple)";
        }

        return `<div>
            <span class="text-muted">[${escapeHtml(time)}]</span>
            <strong style="color: ${eventColor};">${escapeHtml(e.eventType)}</strong>:
            Peer=${escapeHtml(e.peerId || '')}
            Msg=${escapeHtml(e.messageId || '-')}
            Remote=${escapeHtml(e.remotePeerId || '-')}
        </div>`;
    }).join("");
}

// --------------------------------------------------------------------------
// API Mutations
// --------------------------------------------------------------------------
async function updateStrategyOnServer(strategy, wP, wT, wB) {
    try {
        const headers = { "Content-Type": "application/json" };
        if (state.apiToken) headers["X-CampusMesh-Token"] = state.apiToken;

        const res = await fetch(`${API_BASE}/api/strategy`, {
            method: "POST",
            headers,
            body: JSON.stringify({ strategy, wP, wT, wB })
        }).then(r => r.json());

        if (res.success) {
            showToast(`Strategy updated to ${res.strategy}`);
        } else {
            showToast(res.message || "Failed to update strategy", "danger");
        }
    } catch (err) {
        console.error("Strategy update failed:", err);
    }
}

async function submitMessage() {
    const srcInput = document.getElementById("composeSource");
    const source = (srcInput && srcInput.value.trim()) || state.activeNodeId || "LAPTOP_WIN";
    const dest = document.getElementById("composeDest").value.trim();
    const priority = document.getElementById("composePriority").value;
    const ttlSeconds = parseInt(document.getElementById("composeTtl").value);
    const copyTokens = parseInt(document.getElementById("composeTokens").value);
    const payload = document.getElementById("composePayload").value.trim();

    if (!dest) {
        showToast("Please enter a destination peer ID", "warning");
        return;
    }
    if (!payload) {
        showToast("Please enter a message payload", "warning");
        return;
    }

    try {
        const headers = { "Content-Type": "application/json" };
        if (state.apiToken) headers["X-CampusMesh-Token"] = state.apiToken;

        const response = await fetch(`${API_BASE}/api/messages`, {
            method: "POST",
            headers,
            body: JSON.stringify({ sourceId: source, destinationId: dest, priority, ttlSeconds, copyTokens, payload })
        });
        const res = await response.json();

        if (response.ok && res.success) {
            showToast(`[${source} ➔ ${dest}] Dispatched ${res.messageId}!`, "success");
            triggerPacketAnimation(source, dest);
            fetchData();
        } else {
            showToast(res.message || "Error creating message", "danger");
        }
    } catch (err) {
        showToast("Error creating message: " + err.message, "danger");
    }
}

window.scanRadiosAndSync = async function() {
    try {
        showToast("Broadcasting beacon on 239.255.77.88:5354 & scanning BLE...", "info");
        await fetchData();
        const myId = state.myNodeId || "";
        const onlineCount = (state.peers || []).filter(p => !p.isExpired && p.peerId !== myId).length;
        if (onlineCount > 0) {
            showToast(`Discovered ${onlineCount} active peer(s) in local mesh!`, "success");
        } else {
            showToast("No other mesh nodes in range. Connect another phone or laptop to Wi-Fi to appear on radar.", "info");
        }
    } catch (e) {
        console.error(e);
    }
};

// --------------------------------------------------------------------------
// Interactive HTML5 Canvas Radar Topology
// --------------------------------------------------------------------------
let canvas, ctx;
let radarAngle = 0;

function initCanvas() {
    canvas = document.getElementById("meshCanvas");
    if (!canvas) return;
    ctx = canvas.getContext("2d");

    // Click handler for nodes
    canvas.addEventListener("click", (e) => {
        const rect = canvas.getBoundingClientRect();
        const scaleX = canvas.width / rect.width;
        const scaleY = canvas.height / rect.height;
        const mouseX = (e.clientX - rect.left) * scaleX;
        const mouseY = (e.clientY - rect.top) * scaleY;

        for (const [id, n] of Object.entries(nodes)) {
            const dist = Math.hypot(n.x - mouseX, n.y - mouseY);
            if (dist <= 28) {
                selectPeer(id);
                return;
            }
        }
    });

    // Hover handler for cursor changes
    canvas.addEventListener("mousemove", (e) => {
        const rect = canvas.getBoundingClientRect();
        const scaleX = canvas.width / rect.width;
        const scaleY = canvas.height / rect.height;
        const mouseX = (e.clientX - rect.left) * scaleX;
        const mouseY = (e.clientY - rect.top) * scaleY;

        let hit = null;
        for (const [id, n] of Object.entries(nodes)) {
            const dist = Math.hypot(n.x - mouseX, n.y - mouseY);
            if (dist <= 28) {
                hit = id;
                break;
            }
        }
        state.hoveredNode = hit;
        canvas.style.cursor = hit ? "pointer" : "default";
    });

    canvas.addEventListener("mouseleave", () => {
        state.hoveredNode = null;
    });

    requestAnimationFrame(renderCanvasLoop);
}

function triggerPacketAnimation(fromId, toId) {
    const localId = state.activeNodeId || state.localPeer.id || "LAPTOP_WIN";
    const from = nodes[fromId] || nodes[localId] || { x: 300, y: 190 };
    const to = nodes[toId] || nodes[localId] || { x: 300, y: 190 };

    state.animPackets.push({
        fromX: from.x, fromY: from.y,
        toX: to.x, toY: to.y,
        progress: 0,
        speed: 0.025
    });
}

// Preserved function name explicitly required by test suite:
function renderCanvasLoop() {
    if (!ctx || !canvas) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const isLight = document.body.classList.contains("light-theme");
    const radarRingColor = isLight ? "rgba(0,0,0,0.06)" : "rgba(255,255,255,0.06)";
    const textColor = isLight ? "#0f172a" : "#f8fafc";
    const subtextColor = isLight ? "#475569" : "#94a3b8";

    const cx = canvas.width / 2;
    const cy = canvas.height / 2;

    // 1. Draw Radar Concentric Rings
    [55, 115, 175, 235].forEach(r => {
        ctx.beginPath();
        ctx.arc(cx, cy, r, 0, Math.PI * 2);
        ctx.strokeStyle = radarRingColor;
        ctx.lineWidth = 1;
        ctx.stroke();
    });

    // 2. Radar Crosshair Grids
    ctx.save();
    ctx.strokeStyle = isLight ? "rgba(0,0,0,0.04)" : "rgba(255,255,255,0.04)";
    ctx.setLineDash([3, 5]);
    ctx.beginPath();
    ctx.moveTo(cx - 235, cy);
    ctx.lineTo(cx + 235, cy);
    ctx.moveTo(cx, cy - 235);
    ctx.lineTo(cx, cy + 235);
    ctx.stroke();
    ctx.restore();

    // 3. Radar Sweep Beam
    radarAngle += 0.015;
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.arc(cx, cy, 235, radarAngle, radarAngle + 0.38);
    ctx.closePath();
    const beamGradient = ctx.createRadialGradient(cx, cy, 10, cx, cy, 235);
    beamGradient.addColorStop(0, "rgba(56, 189, 248, 0.16)");
    beamGradient.addColorStop(1, "rgba(56, 189, 248, 0)");
    ctx.fillStyle = beamGradient;
    ctx.fill();
    ctx.restore();

    // 4. Count Online Remote Peers
    const localId = state.activeNodeId || state.localPeer.id || "LAPTOP_WIN";
    const onlinePeerKeys = Object.keys(nodes).filter(k => k !== localId);

    // If 0 peers are online, render informative scanning indicator
    if (onlinePeerKeys.length === 0) {
        ctx.save();
        ctx.textAlign = "center";
        ctx.font = "600 11px " + getComputedStyle(document.body).fontFamily;
        ctx.fillStyle = isLight ? "#64748b" : "#94a3b8";
        ctx.fillText("SCANNING FOR MESH PEERS...", cx, cy + 72);

        ctx.font = "9px " + getComputedStyle(document.body).fontFamily;
        ctx.fillStyle = isLight ? "#94a3b8" : "#64748b";
        ctx.fillText("0 remote peers online • Wi-Fi mDNS & BLE active", cx, cy + 86);
        ctx.restore();
    }

    // 5. Draw Dynamic Links between Local Node and Online Peers
    links.forEach(link => {
        const from = nodes[link.from];
        const to = nodes[link.to];
        if (!from || !to) return;

        ctx.save();
        ctx.beginPath();
        ctx.moveTo(from.x, from.y);
        ctx.lineTo(to.x, to.y);

        // Underglow if link is in active contact
        if (link.inContact) {
            ctx.save();
            ctx.strokeStyle = "rgba(16, 185, 129, 0.3)";
            ctx.lineWidth = 5;
            ctx.stroke();
            ctx.restore();
        }

        if (link.type === "WIFI") {
            ctx.strokeStyle = "rgba(56, 189, 248, 0.45)";
            ctx.setLineDash([6, 6]);
            ctx.lineWidth = 2;
        } else {
            ctx.strokeStyle = "rgba(168, 85, 247, 0.5)";
            ctx.setLineDash([3, 4]);
            ctx.lineWidth = 1.5;
        }
        ctx.stroke();
        ctx.restore();
    });

    // 6. Draw Animated Packets
    state.animPackets = state.animPackets.filter(p => {
        p.progress += p.speed;
        const currX = p.fromX + (p.toX - p.fromX) * p.progress;
        const currY = p.fromY + (p.toY - p.fromY) * p.progress;

        ctx.save();
        ctx.beginPath();
        ctx.arc(currX, currY, 6, 0, Math.PI * 2);
        ctx.fillStyle = "#38bdf8";
        ctx.shadowColor = "#38bdf8";
        ctx.shadowBlur = 12;
        ctx.fill();
        ctx.restore();

        return p.progress < 1.0;
    });

    // 7. Draw Nodes (Local Node + Strictly Online Peers)
    for (const [id, n] of Object.entries(nodes)) {
        ctx.save();

        const isLocalNode = (id === state.activeNodeId || n.isLocal);
        const isHovered = (id === state.hoveredNode);

        // Hover glow
        if (isHovered) {
            ctx.beginPath();
            ctx.arc(n.x, n.y, 28, 0, Math.PI * 2);
            ctx.fillStyle = "rgba(56, 189, 248, 0.18)";
            ctx.fill();
        }

        // Active contact halo
        if (n.inContact && !isLocalNode) {
            ctx.beginPath();
            ctx.arc(n.x, n.y, 26, 0, Math.PI * 2);
            ctx.strokeStyle = "rgba(16, 185, 129, 0.45)";
            ctx.lineWidth = 2;
            ctx.stroke();
        }

        // Node Outer Ring
        ctx.beginPath();
        ctx.arc(n.x, n.y, 22, 0, Math.PI * 2);
        if (isLocalNode) {
            ctx.fillStyle = "rgba(16, 185, 129, 0.28)";
            ctx.strokeStyle = "#10b981";
            ctx.lineWidth = 3;
        } else if (n.platform === "ANDROID") {
            ctx.fillStyle = "rgba(56, 189, 248, 0.22)";
            ctx.strokeStyle = "#38bdf8";
            ctx.lineWidth = 2;
        } else if (n.platform === "IOS") {
            ctx.fillStyle = "rgba(168, 85, 247, 0.22)";
            ctx.strokeStyle = "#a855f7";
            ctx.lineWidth = 2;
        } else if (n.platform === "MOBILE") {
            ctx.fillStyle = "rgba(236, 72, 153, 0.22)";
            ctx.strokeStyle = "#ec4899";
            ctx.lineWidth = 2;
        } else {
            ctx.fillStyle = "rgba(245, 158, 11, 0.22)";
            ctx.strokeStyle = "#f59e0b";
            ctx.lineWidth = 2;
        }
        ctx.fill();
        ctx.stroke();

        // Node Center Core
        ctx.beginPath();
        ctx.arc(n.x, n.y, 7, 0, Math.PI * 2);
        ctx.fillStyle = ctx.strokeStyle;
        ctx.fill();

        // Node Label
        ctx.font = "bold 11px " + getComputedStyle(document.body).fontFamily;
        ctx.fillStyle = textColor;
        ctx.textAlign = "center";
        const labelText = isLocalNode ? `${n.label} (YOU)` : n.label;
        ctx.fillText(labelText, n.x, n.y + 36);

        // Subtitle (Platform & Online Status)
        ctx.font = "9px " + getComputedStyle(document.body).fontFamily;
        ctx.fillStyle = isLocalNode ? "var(--accent-green)" : subtextColor;
        const subText = isLocalNode ? "OPERATING NODE" : `${n.platform} • ONLINE`;
        ctx.fillText(subText, n.x, n.y + 48);

        ctx.restore();
    }

    requestAnimationFrame(renderCanvasLoop);
}

// --------------------------------------------------------------------------
// Helpers
// --------------------------------------------------------------------------
function showToast(message, type = "info") {
    const container = document.getElementById("toastContainer");
    if (!container) return;

    const toast = document.createElement("div");
    let icon = "⚡";
    if (type === "success") icon = "✅";
    if (type === "danger") icon = "❌";
    if (type === "warning") icon = "⚠️";

    toast.className = `toast ${type === 'danger' ? 'text-danger' : ''}`;
    toast.innerHTML = `<span>${icon}</span> <span>${escapeHtml(message)}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.classList.add("toast-closing");
        setTimeout(() => toast.remove(), 250);
    }, 3400);
}

function escapeHtml(text) {
    if (!text) return "";
    return String(text).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function crc32(str) {
    let crc = -1;
    for (let i = 0; i < str.length; i++) {
        crc = (crc >>> 8) ^ crcTable[(crc ^ str.charCodeAt(i)) & 0xFF];
    }
    return (crc ^ (-1)) >>> 0;
}

const crcTable = (() => {
    let c;
    const table = [];
    for (let n = 0; n < 256; n++) {
        c = n;
        for (let k = 0; k < 8; k++) {
            c = ((c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1));
        }
        table[n] = c;
    }
    return table;
})();
