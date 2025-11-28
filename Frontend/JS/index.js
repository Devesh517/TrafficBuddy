 // Global timeout for the message box
        let messageTimeout;

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
            attribution: "© OpenStreetMap contributors",
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
            if (isDark) document.body.setAttribute("data-theme", "dark");
            else document.body.removeAttribute("data-theme");
        }

        // =====================================
        // 3. MAIN ROUTE FUNCTION – BACKEND LINK (FIXED: removed alert())
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

            const btn = document.querySelector(".btn-primary");
            btn.innerHTML = "Searching...";
            btn.disabled = true;

            try {
                const response = await fetch("http://localhost:8080/api/route/search", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        start: start,
                        end: end,
                        avoidTolls: avoidTolls,
                        avoidHighways: avoidHighways
                    }),
                });

                if (!response.ok) {
                    const err = await response.text();
                    // Display the precise error message returned by the backend (e.g., "Start location not found...")
                    throw new Error(err.includes("not found") ? err : "Backend Error: " + err);
                }

                const data = await response.json();
                displayRoute(data);

            } catch (error) {
                console.error("Frontend Route Error:", error);
                // Display specific error if available, otherwise generic
                showMessage(`Could not fetch route: ${error.message || "Check if backend is running and API keys are correct."}`, 10000);
            } finally {
                btn.innerHTML = "Find Best Route";
                btn.disabled = false;
            }
        }

        // =====================================
        // 4. DISPLAY ROUTE ON MAP (FIXED: removed alert())
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
                color: "#007bff",
                weight: 6,
                opacity: 0.85,
            }).addTo(map);

            startMarker = L.marker(coords[0]).addTo(map).bindPopup("Start");
            endMarker = L.marker(coords[coords.length - 1]).addTo(map).bindPopup("Destination");

            map.fitBounds(routeLayer.getBounds(), { padding: [50, 50] });

            // Sidebar Directions
            const list = document.getElementById("directionsList");
            list.innerHTML = `
                <div style="padding:10px; background:#e3f2fd; border-radius:8px; margin-bottom:15px; font-size:13px; color: var(--text-dark);">
                    🌦 Weather: <strong>${data.weatherInfo || "N/A"}</strong><br>
                    📏 <strong>${(data.distance / 1000).toFixed(1)} km</strong> • 
                    ⏱ <strong>${Math.round(data.duration / 60)} min</strong>
                </div>
            `;

            if (data.directions?.length > 0) {
                data.directions.forEach((step, index) => {
                    const div = document.createElement("div");
                    div.className = "direction-step";
                    div.innerHTML = `<b>${index + 1}. </b> ${step}`;
                    list.appendChild(div);
                });
            } else {
                list.innerHTML += `<div style="color:var(--error-red); padding:10px;">No step-by-step directions found.</div>`;
            }
        }

        // =====================================
        // 5. CLEAR MAP + SIDEBAR
        // =====================================
        function clearMap() {
            if (routeLayer) map.removeLayer(routeLayer);
            if (startMarker) map.removeLayer(startMarker);
            if (endMarker) map.removeLayer(endMarker);

            document.getElementById("directionsList").innerHTML = `
                <div style="text-align:center; color:#999; margin-top:50px;">
                    Enter locations to view route
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
                const response = await fetch("http://localhost:8080/api/chat", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ message: text }),
                });
                
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

        console.log("TrafficBuddy: Frontend Loaded ✓ Backend URL → http://localhost:8080");
