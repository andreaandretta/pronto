import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.jsx';
import './index.css';

console.log('PRONTO: main.jsx loaded');

try {
  const root = ReactDOM.createRoot(document.getElementById('root'));
  root.render(<App />);
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
