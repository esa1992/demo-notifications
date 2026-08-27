"use strict";

const http = require("http");
const express = require("express");
const { WebSocketServer } = require("ws");

const PORT = Number(process.env.PORT) || 10000;
const HOST = "0.0.0.0";
const MAX_HISTORY = 200;

const app = express();
app.use(express.json({ limit: "1mb" }));
app.use((req, res, next) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") {
    res.sendStatus(204);
    return;
  }
  next();
});

/** @type {object[]} */
const history = [];

function asArray(body) {
  if (body == null) return [];
  return Array.isArray(body) ? body : [body];
}

function matchesCustomer(item, customerId) {
  if (!customerId) return true;
  return String(item.customerId ?? "") === String(customerId);
}

function sendJson(ws, payload) {
  if (ws.readyState === 1) {
    ws.send(JSON.stringify(payload));
  }
}

const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: "/ws" });

wss.on("connection", (ws, req) => {
  const url = new URL(req.url, "http://localhost");
  ws.customerId = url.searchParams.get("customerId") || "";
  ws.isAlive = true;

  ws.on("pong", () => {
    ws.isAlive = true;
  });

  sendJson(ws, {
    type: "snapshot",
    items: history.filter((item) => matchesCustomer(item, ws.customerId)),
  });
});

const heartbeat = setInterval(() => {
  for (const ws of wss.clients) {
    if (ws.isAlive === false) {
      ws.terminate();
      continue;
    }
    ws.isAlive = false;
    ws.ping();
  }
}, 25000);

wss.on("close", () => clearInterval(heartbeat));

function broadcastLive(items) {
  for (const ws of wss.clients) {
    const filtered = items.filter((item) => matchesCustomer(item, ws.customerId));
    if (filtered.length > 0) {
      sendJson(ws, { type: "notifications", items: filtered });
    }
  }
}

app.get("/health", (_req, res) => {
  res.json({
    status: "ok",
    clients: wss.clients.size,
    stored: history.length,
  });
});

app.get("/", (_req, res) => {
  res.json({
    service: "demo-notifications",
    health: "/health",
    post: "/notifications",
    list: "/notifications",
    websocket: "/ws",
  });
});

app.get("/notifications", (req, res) => {
  const customerId = String(req.query.customerId || "");
  const items = history.filter((item) => matchesCustomer(item, customerId));
  res.json({ count: items.length, items });
});

app.post("/notifications", (req, res) => {
  const items = asArray(req.body).filter((item) => item && typeof item === "object");
  if (items.length === 0) {
    res.status(400).json({ error: "empty body" });
    return;
  }
  history.push(...items);
  if (history.length > MAX_HISTORY) {
    history.splice(0, history.length - MAX_HISTORY);
  }
  broadcastLive(items);
  res.json({ accepted: items.length, clients: wss.clients.size });
});

server.listen(PORT, HOST, () => {
  console.log(`demo-notifications listening on ${HOST}:${PORT}`);
});

function shutdown() {
  clearInterval(heartbeat);
  for (const ws of wss.clients) {
    ws.close(1001, "server shutting down");
  }
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 5000).unref();
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
