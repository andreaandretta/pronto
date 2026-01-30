import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import MainActivityPreview from './MainActivityPreview.jsx'
import './index.css'

// Switcher per vedere overlay o homepage
function AppSwitcher() {
  const [view, setView] = React.useState('overlay')

  return (
    <div>
      {/* Toggle Switcher */}
      <div className="fixed top-4 right-4 z-50 bg-slate-800 rounded-lg p-2 shadow-xl">
        <div className="flex gap-2">
          <button
            onClick={() => setView('overlay')}
            className={`px-4 py-2 rounded font-medium transition-all ${
              view === 'overlay'
                ? 'bg-teal-500 text-white'
                : 'bg-slate-700 text-slate-300 hover:bg-slate-600'
            }`}
          >
            Overlay
          </button>
          <button
            onClick={() => setView('homepage')}
            className={`px-4 py-2 rounded font-medium transition-all ${
              view === 'homepage'
                ? 'bg-teal-500 text-white'
                : 'bg-slate-700 text-slate-300 hover:bg-slate-600'
            }`}
          >
            Homepage
          </button>
        </div>
      </div>

      {/* Content */}
      {view === 'overlay' ? <App /> : <MainActivityPreview />}
    </div>
  )
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <AppSwitcher />
  </React.StrictMode>
)