import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Navbar } from './components/Navbar'
import { LeaderboardPage } from './pages/LeaderboardPage'
import { ChannelDetailPage } from './pages/ChannelDetailPage'

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-gray-50">
        <Navbar />
        <Routes>
          <Route path="/" element={<LeaderboardPage />} />
          <Route path="/channel/:channelId" element={<ChannelDetailPage />} />
        </Routes>
      </div>
    </BrowserRouter>
  )
}
