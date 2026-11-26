import express from "express";
import http from "http";
import cors from "cors";
import { Server } from "socket.io";

const app = express();

app.use(cors());

const server = http.createServer(app);

const io = new Server(server, {
  cors: {
    origin: "*"
  }
});

let currentQuestion = "";
let reveal = false;

const players = {};

function broadcastState() {
  io.emit("state", {
    question: currentQuestion,
    reveal,
    players
  });
}

io.on("connection", (socket) => {

  socket.on("join", (name) => {
    players[socket.id] = {
      name,
      answer: "",
      score: 0
    };

    broadcastState();
  });

  socket.on("answer", (answer) => {
    if (!players[socket.id]) return;

    players[socket.id].answer = answer;

    broadcastState();
  });

  socket.on("setQuestion", (question) => {
    currentQuestion = question;
    reveal = false;

    Object.values(players).forEach((p) => {
      p.answer = "";
    });

    broadcastState();
  });

  socket.on("revealAnswers", () => {
    reveal = true;
    broadcastState();
  });

  socket.on("resetAnswers", () => {
    reveal = false;

    Object.values(players).forEach((p) => {
      p.answer = "";
    });

    broadcastState();
  });

  socket.on("addPoint", (id) => {
    if (players[id]) {
      players[id].score += 1;
      broadcastState();
    }
  });

  socket.on("disconnect", () => {
    delete players[socket.id];
    broadcastState();
  });

  broadcastState();
});

server.listen(3001, () => {
  console.log("Server running on port 3001");
});
