// =====================================
// TrafficBuddy — Dashboard Logic
// =====================================

const API_BASE = "http://localhost:8080";

let messageTimeout;

// =====================================
// 0. AUTH GUARD
// =====================================
// NEW: the backend now guards every /api/... call with a signin token (AuthFilter +
// AuthService). Without a token stored from the login page, don't even load the
// dashboard — send the user to log in first.
let AUTH_TOKEN = localStorage.getItem("trafficBuddyToken");
if (!AUTH_TOKEN) {
    window.location.href = "../HTML/login.html";
}

// Builds fetch headers with the bearer token attached, merged with any extra headers.
function authHeaders(extra = {}) {
    return Object.assign({}, extra, { Authorization: `Bearer ${AUTH_TOKEN}` });
}

// If the backend rejects a request as unauthorized (missing/expired token), clear the
// stale session and bounce back to the login page rather than leaving the user stuck on
// a dashboard that can no longer reach the backend.
function handleUnauthorized() {
    localStorage.removeItem("trafficBuddyToken");
    localStorage.removeItem("trafficBuddyCurrentUser");
    showMessage("⚠️ Your session has expired. Please log in again.", 4000);
    setTimeout(() => {
        window.location.href = "../HTML/login.html";
    }, 1500);
}

function logout() {
    localStorage.removeItem("trafficBuddyToken");
    localStorage.removeItem("trafficBuddyCurrentUser");
    window.location.href = "../HTML/login.html";
}

// =====================================
// UTILITY: Custom Message Box (Replaces alert())
// =====================================
function showMessage(text, duration = 5000) {
    const box = document.getElementById("messageBox");
    clearTimeout(messageTimeout);

    box.innerText = text;
    box.classList.add("active");

    messageTimeout = setTimeout(() => {
        box.classList.remove("active");
    }, duration);
}

// =====================================
// 1. MAP INITIALIZATION
// =====================================
const map = L.map("map").setView([26.4499, 74.6399], 13);

L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    attribution: "&copy; OpenStreetMap contributors",
    maxZoom: 19,
}).addTo(map);

let routeLayer = null;
let startMarker = null;
let endMarker = null;

// =====================================
// 2. UI TOGGLE FUNCTIONS
// =====================================
function toggleSettings() {
    document.getElementById("settingsPanel").classList.toggle("active");
}

function toggleChat() {
    document.getElementById("chatWindow").classList.toggle("active");
}

function toggleDarkMode() {
    const isDark = document.getElementById("darkModeToggle").checked;
    if (isDark) {
        document.body.setAttribute("data-theme", "dark");
        localStorage.setItem("trafficBuddyTheme", "dark");
    } else {
        document.body.removeAttribute("data-theme");
        localStorage.setItem("trafficBuddyTheme", "light");
    }
}

function toggleSidebar() {
    document.getElementById("sidebar").classList.toggle("open");
    document.getElementById("sidebarBackdrop").classList.toggle("show");
}

function setLoading(isLoading) {
    const btn = document.getElementById("findRouteBtn");
    const btnText = document.getElementById("findRouteBtnText");
    const dots = document.getElementById("signalDots");

    btn.disabled = isLoading;
    btnText.textContent = isLoading ? "Searching…" : "Find Best Route";
    dots.classList.toggle("loading", isLoading);
}

// Restore saved theme on load
(function restoreTheme() {
    if (localStorage.getItem("trafficBuddyTheme") === "dark") {
        document.body.setAttribute("data-theme", "dark");
        const toggle = document.getElementById("darkModeToggle");
        if (toggle) toggle.checked = true;
    }
})();

// Show who's logged in, if the sidebar has a place for it
(function showCurrentUser() {
    const el = document.getElementById("currentUserLabel");
    const user = localStorage.getItem("trafficBuddyCurrentUser");
    if (el && user) el.textContent = user;
})();

// =====================================
// 3. MAIN ROUTE FUNCTION – BACKEND LINK
// =====================================
async function findRoute() {
    const start = document.getElementById("startName").value.trim();
    const end = document.getElementById("endName").value.trim();
    const avoidTolls = document.getElementById("avoidTolls").checked;
    const avoidHighways = document.getElementById("avoidHighways").checked;

    if (!start || !end) {
        showMessage("🛑 Please enter both the starting point and the destination.");
        return;
    }

    setLoading(true);

    // DEBUG: log exactly what's being sent, so it's obvious from devtools whether this
    // was a request problem (bad payload / wrong API_BASE) vs a backend/geocoding problem.
    console.log("TrafficBuddy: searching route", { API_BASE, start, end, avoidTolls, avoidHighways });

    try {
        let response;
        try {
            response = await fetch(`${API_BASE}/api/route/search`, {
                method: "POST",
                headers: authHeaders({ "Content-Type": "application/json" }),
                body: JSON.stringify({
                    start: start,
                    end: end,
                    avoidTolls: avoidTolls,
                    avoidHighways: avoidHighways,
                }),
            });
        } catch (networkErr) {
            // fetch() itself threw — this is a connectivity/CORS problem, NOT a location
            // problem. Surface that distinction clearly instead of a generic message, since
            // "Failed to fetch" looks a lot like "location not found" if you don't separate them.
            console.error("TrafficBuddy: network/CORS error reaching backend", networkErr);
            showMessage(
                `🔌 Could not reach the backend at ${API_BASE}. Is it running, and is this device pointed at the right address?`,
                10000
            );
            return;
        }

        if (response.status === 401) {
            handleUnauthorized();
            return;
        }

        if (!response.ok) {
            const err = await response.text();
            console.warn("TrafficBuddy: backend rejected route search", response.status, err);
            // Display the precise error message returned by the backend (e.g., "Start location not found...")
            throw new Error(err.includes("not found") ? err : "Backend Error: " + err);
        }

        const data = await response.json();
        console.log("TrafficBuddy: route search succeeded", data);
        displayRoute(data);

    } catch (error) {
        console.error("Frontend Route Error:", error);
        showMessage(`Could not fetch route: ${error.message || "Check if backend is running and API keys are correct."}`, 10000);
    } finally {
        setLoading(false);
    }
}

// Allow pressing Enter inside either location field to search
["startName", "endName"].forEach((id) => {
    const el = document.getElementById(id);
    if (el) {
        el.addEventListener("keypress", (e) => {
            if (e.key === "Enter") findRoute();
        });
    }
});

// =====================================
// 4. DISPLAY ROUTE ON MAP
// =====================================
function displayRoute(data) {
    clearMap();

    if (!data.route || !data.route.coordinates || data.route.coordinates.length === 0) {
        showMessage("⚠️ No route found. Please try different locations.", 8000);
        return;
    }

    // Convert ORS coordinates for Leaflet
    const coords = data.route.coordinates.map((c) => [c.lat, c.lng]);

    routeLayer = L.polyline(coords, {
        color: "#00b8d9",
        weight: 6,
        opacity: 0.9,
        lineCap: "round",
        lineJoin: "round",
    }).addTo(map);

    startMarker = L.marker(coords[0]).addTo(map).bindPopup("Start");
    endMarker = L.marker(coords[coords.length - 1]).addTo(map).bindPopup("Destination");

    map.fitBounds(routeLayer.getBounds(), { padding: [60, 60] });

    // Close the mobile sidebar drawer after a successful search so the map is visible
    if (window.innerWidth <= 860) {
        document.getElementById("sidebar").classList.remove("open");
        document.getElementById("sidebarBackdrop").classList.remove("show");
    }

    renderSummary(data);
    renderDirections(data.directions);
}

// WMO weather codes (used by Open-Meteo and similar APIs) -> readable text + icon
const WEATHER_CODES = {
    0: ["Clear sky", "☀️"],
    1: ["Mainly clear", "🌤️"],
    2: ["Partly cloudy", "⛅"],
    3: ["Overcast", "☁️"],
    45: ["Fog", "🌫️"],
    48: ["Rime fog", "🌫️"],
    51: ["Light drizzle", "🌦️"],
    53: ["Drizzle", "🌦️"],
    55: ["Dense drizzle", "🌦️"],
    56: ["Freezing drizzle", "🌧️"],
    57: ["Freezing drizzle", "🌧️"],
    61: ["Light rain", "🌧️"],
    63: ["Rain", "🌧️"],
    65: ["Heavy rain", "🌧️"],
    66: ["Freezing rain", "🌧️"],
    67: ["Freezing rain", "🌧️"],
    71: ["Light snow", "🌨️"],
    73: ["Snow", "🌨️"],
    75: ["Heavy snow", "🌨️"],
    77: ["Snow grains", "🌨️"],
    80: ["Light showers", "🌦️"],
    81: ["Showers", "🌦️"],
    82: ["Heavy showers", "⛈️"],
    85: ["Snow showers", "🌨️"],
    86: ["Heavy snow showers", "🌨️"],
    95: ["Thunderstorm", "⛈️"],
    96: ["Thunderstorm w/ hail", "⛈️"],
    99: ["Thunderstorm w/ hail", "⛈️"],
};

// Normalizes whatever shape the backend sends the weather in — a clean
// { temperature, windSpeed, condition } object, a raw Open-Meteo style
// payload (possibly nested under current_weather / current), or a JSON
// string of either — into one consistent object the UI can render.
function extractWeather(data) {
    let raw = data.weather;

    if (typeof raw === "string") {
        try { raw = JSON.parse(raw); } catch { raw = null; }
    }
    if (!raw && typeof data.weatherInfo === "string") {
        try { raw = JSON.parse(data.weatherInfo); } catch { raw = null; }
    }
    if (!raw || typeof raw !== "object") return null;

    // Already-clean shape
    if (raw.temperature != null && (raw.condition || raw.windSpeed != null)) {
        return {
            temperature: raw.temperature,
            windSpeed: raw.windSpeed,
            condition: raw.condition || "N/A",
            icon: "🌦️",
        };
    }

    // Raw forecast-API shape, temperature/wind may live under current_weather / current
    const cw = raw.current_weather || raw.current || raw;
    const temperature = cw.temperature ?? cw.temperature_2m;
    const windSpeed = cw.windspeed ?? cw.wind_speed_10m ?? cw.windSpeed;
    const code = cw.weathercode ?? cw.weather_code;

    if (temperature == null && windSpeed == null && code == null) return null;

    const [conditionText, icon] = WEATHER_CODES[code] || ["N/A", "🌦️"];
    return {
        temperature: temperature != null ? Math.round(temperature * 10) / 10 : null,
        windSpeed: windSpeed != null ? Math.round(windSpeed) : null,
        condition: conditionText,
        icon,
    };
}

function renderSummary(data) {
    const list = document.getElementById("directionsList");
    const weather = extractWeather(data);
    const distanceKm = data.distance != null ? (data.distance / 1000).toFixed(1) : "--";
    const durationMin = data.duration != null ? Math.round(data.duration / 60) : "--";

    let weatherHtml = "";
    if (weather) {
        weatherHtml = `
            <div class="weather-row"><span class="icon">${weather.icon}</span> ${weather.condition}</div>
            <div class="weather-detail">🌡 Temperature: <strong>${weather.temperature ?? "--"}°C</strong></div>
            <div class="weather-detail">💨 Wind: <strong>${weather.windSpeed ?? "--"} km/h</strong></div>
        `;
    }

    list.innerHTML = `
        <div class="summary-card">
            ${weatherHtml}
            <div class="summary-stats">
                <div class="stat-chip">
                    <span class="value">${distanceKm}</span>
                    <span class="label">km</span>
                </div>
                <div class="stat-chip">
                    <span class="value">${durationMin}</span>
                    <span class="label">min</span>
                </div>
            </div>
        </div>
    `;
}

function renderDirections(directions) {
    const list = document.getElementById("directionsList");

    if (directions && directions.length > 0) {
        directions.forEach((step, index) => {
            const div = document.createElement("div");
            div.className = "direction-step";
            div.innerHTML = `<span class="step-num">${index + 1}</span><span class="step-text">${step}</span>`;
            list.appendChild(div);
        });
    } else {
        const div = document.createElement("div");
        div.className = "no-directions";
        div.textContent = "No step-by-step directions found.";
        list.appendChild(div);
    }
}

// =====================================
// 5. CLEAR MAP + SIDEBAR
// =====================================
function clearMap() {
    if (routeLayer) map.removeLayer(routeLayer);
    if (startMarker) map.removeLayer(startMarker);
    if (endMarker) map.removeLayer(endMarker);
    routeLayer = null;
    startMarker = null;
    endMarker = null;

    document.getElementById("directionsList").innerHTML = `
        <div class="empty-state">
            <span class="icon">🗺️</span>
            Enter a starting point and destination to see your route, live weather, and step-by-step directions.
        </div>
    `;
}

// =====================================
// 6. CHATBOT LOGIC – BACKEND CONNECTED
// =====================================
function handleChatEnter(e) {
    if (e.key === "Enter") sendMessage();
}

async function sendMessage() {
    const input = document.getElementById("chatInput");
    const text = input.value.trim();
    if (!text) return;

    addMessage(text, "user");
    input.value = "";

    try {
        const response = await fetch(`${API_BASE}/api/chat`, {
            method: "POST",
            headers: authHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({ message: text }),
        });

        if (response.status === 401) {
            handleUnauthorized();
            return;
        }

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(errorText);
        }

        const data = await response.json();
        addMessage(data.reply, "bot");

    } catch (error) {
        console.error("Chat error:", error);
        addMessage(`Chat server not responding. Please check backend. Error: ${error.message || "Unknown."}`, "bot");
    }
}

function addMessage(text, type) {
    const body = document.getElementById("chatBody");
    const msg = document.createElement("div");
    msg.className = `msg ${type}`;
    msg.innerText = text;
    body.appendChild(msg);
    body.scrollTop = body.scrollHeight;
}

console.log("TrafficBuddy: Frontend Loaded ✓ Backend URL → " + API_BASE);