import { Link } from 'react-router-dom'

export function Navbar() {
  return (
    <header className="bg-white border-b border-gray-200 sticky top-0 z-10">
      <div className="px-6 h-[3.33rem] flex items-center">
        <Link to="/">
          <img src="/logo.png" alt="TubeReturns" className="h-36 rounded" />
        </Link>
      </div>
    </header>
  )
}
