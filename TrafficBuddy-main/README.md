# TrafficBuddy – Real-Time Smart Route & Traffic Assistant

TrafficBuddy is a smart navigation assistant that helps users find the fastest and most efficient routes using OpenRouteService API along with real-time traffic insights. It provides a clean UI, map integration, and options to avoid tolls and highways.

## Features
- Live map using Leaflet.js
- Real-time traffic information
- Smart route calculation
- Avoid tolls and highways option
- Clean and responsive UI
- Spring Boot backend with WebClient
- OpenRouteService integration
- Easy deployment on Render / Netlify / Vercel

## Tech Stack

### Frontend
- HTML
- CSS
- JavaScript
- Leaflet.js

### Backend
- Java 17
- Spring Boot
- WebClient
- OpenRouteService API

## Project Structure
TrafficBuddy/
│── Backend/
│   ├── src/main/java/com/example/demo/
│   │      ├── controller/
│   │      ├── service/
│   │      ├── model/
│   │      └── TrafficBuddyApplication.java
│   ├── src/main/resources/application.properties
│   └── pom.xml
│
│── Frontend/
│   ├── index.html
│   ├── style.css
│   ├── script.js
│   └── assets/
│
└── README.md

