import React, { useState } from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.jsx';
import MainActivityPreview from './MainActivityPreview.jsx';
import './index.css';

console.log('PRONTO: main.jsx loaded');

// Check URL for preview mode
const urlParams = new URLSearchParams(window.location.search);
const showMainActivity = urlParams.get('view') === 'main';

function PreviewSwitcher() {
  const [view, setView] = useState(showMainActivity ? 'main' : 'overlay');
  
  return (
    <div>
      {/* View Toggle */}
      <div className="fixed bottom-4 left-1/2 transform -translate-x-1/2 z-50 bg-slate-800 rounded-full p-1 flex gap-1 shadow-xl border border-slate-700">
        <button
          onClick={() => setView('overlay')}
          className={`px-4 py-2 rounded-full text-sm font-medium transition-all ${
            view === 'overlay' ? 'bg-teal-500 text-white' : 'text-slate-400 hover:text-white'
          }`}
        >
          📱 Overlay
        </button>
        <button
          onClick={() => setView('main')}
          className={`px-4 py-2 rounded-full text-sm font-medium transition-all ${
            view === 'main' ? 'bg-teal-500 text-white' : 'text-slate-400 hover:text-white'
          }`}
        >
          🏠 Homepage
        </button>
      </div>
      
      {/* Content */}
      {view === 'overlay' ? <App /> : <MainActivityPreview />}
    </div>
  );
}

try {
  const root = ReactDOM.createRoot(document.getElementById('root'));
  root.render(<PreviewSwitcher />);
  console.log('PRONTO: React rendered successfully');
  
  // Nascondi schermata di caricamento
  if (window.hideLoading) {
    window.hideLoading();
  }
} catch (error) {
  console.error('PRONTO: Error rendering React:', error);
  // Mostra messaggio di errore nativo
  document.body.innerHTML = `
    <div style="
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      background: linear-gradient(135deg, #0d9488 0%, #10b981 100%);
      padding: 20px;
      text-align: center;
      font-family: system-ui, -apple-system, sans-serif;
    ">
      <div style="
        background: white;
        padding: 32px;
        border-radius: 24px;
        box-shadow: 0 25px 50px -12px rgba(0,0,0,0.25);
        max-width: 320px;
      ">
        <p style="color: #dc2626; font-weight: 600; font-size: 18px;">
          ⚠️ Errore di avvio
        </p>
        <p style="color: #6b7280; margin-top: 8px; font-size: 14px;">
          ${error.message}
        </p>
        <button onclick="location.reload()" style="
          margin-top: 16px;
          padding: 12px 24px;
          background: #0d9488;
          color: white;
          border: none;
          border-radius: 12px;
          cursor: pointer;
          font-weight: 600;
        ">
          Riprova
        </button>
      </div>
    </div>
  `;
}
