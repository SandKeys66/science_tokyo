import { useEffect, useState } from "react";
import { io } from "socket.io-client";

const socket = io("http://192.0.0.2:3001");

export default function App() {

  const [name, setName] = useState("");
  const [joined, setJoined] = useState(false);

  const [answer, setAnswer] = useState("");

  const [state, setState] = useState({
    question: "",
    reveal: false,
    players: {}
  });

  const [admin, setAdmin] = useState(false);
  const [questionInput, setQuestionInput] = useState("");

  useEffect(() => {
    socket.on("state", (data) => {
      setState(data);
    });

    return () => socket.off("state");
  }, []);

  const join = () => {
    socket.emit("join", name);
    setJoined(true);
  };

  const submitAnswer = () => {
    socket.emit("answer", answer);
  };

  if (!joined) {
    return (
      <div className="center">
        <h1>QUIZ SHOW</h1>

        <input
          placeholder="名前"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />

        <button onClick={join}>
          参加
        </button>

        <button
          onClick={() => {
            setAdmin(true);
          }}
        >
          司会モード
        </button>
      </div>
    );
  }

  return (
    <div className="container">

      <h1>{state.question}</h1>

      <div className="panel">

        {Object.entries(state.players).map(([id, player]) => (

          <div key={id} className="card">

            <h2>{player.name}</h2>

            <div className="score">
              {player.score}点
            </div>

            <div className="answer">

              {state.reveal
                ? player.answer || "(未回答)"
                : "？？？"}

            </div>

            {admin && (
              <button
                onClick={() => socket.emit("addPoint", id)}
              >
                +1点
              </button>
            )}

          </div>

        ))}

      </div>

      {!admin && (
        <>
          <textarea
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            placeholder="回答を入力"
          />

          <button onClick={submitAnswer}>
            回答送信
          </button>
        </>
      )}

      {admin && (
        <div className="admin">

          <input
            value={questionInput}
            onChange={(e) =>
              setQuestionInput(e.target.value)
            }
            placeholder="問題文"
          />

          <button
            onClick={() =>
              socket.emit(
                "setQuestion",
                questionInput
              )
            }
          >
            出題
          </button>

          <button
            onClick={() =>
              socket.emit("revealAnswers")
            }
          >
            一斉公開
          </button>

          <button
            onClick={() =>
              socket.emit("resetAnswers")
            }
          >
            リセット
          </button>

        </div>
      )}

    </div>
  );
}
