# TrafficBuddy – Real-Time Smart Route & Traffic Assistant

TrafficBuddy is a smart navigation and route-assistance web application designed to help users find efficient routes between locations while providing traffic and weather-related information.

The application combines a responsive web frontend with a Spring Boot backend and external mapping, routing, traffic, and weather services.

---

## 🚦 Features

- 🗺️ Interactive map using Leaflet.js
- 📍 Source and destination based route calculation
- 🚗 Smart route assistance
- 🚦 Traffic information
- 🌦️ Weather information
- 🛣️ Route options such as avoiding highways and toll roads
- 🔐 Backend authentication support
- 🌐 REST API based communication between frontend and backend
- ⚡ Spring Boot backend using WebClient
- 🧭 OpenRouteService integration
- 📍 Google Maps service integration
- 📱 Responsive and user-friendly interface
- ☁️ Deployment-ready project structure

---

## 🛠️ Tech Stack

### Frontend

- HTML5
- CSS3
- JavaScript
- Leaflet.js
- REST API / Fetch API

### Backend

- Java 17
- Spring Boot
- Spring WebFlux
- WebClient
- Maven
- REST APIs

### External Services / APIs

- OpenRouteService API
- Google Maps API
- Weather API

### Development Tools

- Visual Studio Code
- IntelliJ IDEA / Eclipse
- Git
- GitHub

---

# 📂 Project Structure

```text
TrafficBuddy-main/
│
├── Backend/
│   │
│   └── Final Identity Project/
│       │
│       └── Identity AI Agent/
│           │
│           └── demo/
│               │
│               ├── src/
│               │   ├── main/
│               │   │   ├── java/
│               │   │   │   └── com/
│               │   │   │       └── example/
│               │   │   │           └── demo/
│               │   │   │               │
│               │   │   │               ├── config/
│               │   │   │               │   ├── AuthFilter.java
│               │   │   │               │   ├── CorsConfig.java
│               │   │   │               │   └── WebClientConfig.java
│               │   │   │               │
│               │   │   │               ├── controller/
│               │   │   │               │   ├── AuthController.java
│               │   │   │               │   └── RouteController.java
│               │   │   │               │
│               │   │   │               ├── model/
│               │   │   │               │   ├── LoginRequest.java
│               │   │   │               │   ├── RouteRequest.java
│               │   │   │               │   ├── RouteResponse.java
│               │   │   │               │   └── RouteTextRequest.java
│               │   │   │               │
│               │   │   │               ├── service/
│               │   │   │               │   ├── AuthService.java
│               │   │   │               │   ├── GoogleMapsService.java
│               │   │   │               │   ├── OpenRouteService.java
│               │   │   │               │   ├── TrafficService.java
│               │   │   │               │   └── WeatherService.java
│               │   │   │               │
│               │   │   │               └── DemoApplication.java
│               │   │   │
│               │   │   └── resources/
│               │   │       └── application.properties
│               │   │
│               │   └── test/
│               │
│               ├── pom.xml
│               ├── mvnw
│               ├── mvnw.cmd
│               ├── Dockerfile
│               └── HELP.md
│
├── Frontend/
│   │
│   ├── CSS/
│   │   ├── index.css
│   │   └── login.css
│   │
│   ├── HTML/
│   │   ├── index.html
│   │   └── login.html
│   │
│   └── JS/
│       ├── index.js
│       └── login.js
│
├── README.md
└── LICENSE
