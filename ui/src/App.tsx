import { BrowserRouter } from "react-router-dom";
import ChatLayout from "./components/ChatLayout.tsx";
import { ChatSessionsProvider } from "./contexts/ChatSessionsContext.tsx";

function App() {
  return (
    <BrowserRouter>
      <ChatSessionsProvider>
        <ChatLayout />
      </ChatSessionsProvider>
    </BrowserRouter>
  );
}

export default App;
