import { useState } from 'react'

export default function MainActivityPreview() {
  const [isEnabled, setIsEnabled] = useState(true)
  const [overlayPermission, setOverlayPermission] = useState(true)
  const [batteryPermission, setBatteryPermission] = useState(false)

  return (
    <div className="min-h-screen bg-slate-900 p-6 font-sans">
      {/* Header */}
      <div className="flex items-center gap-4 mb-8">
        <span className="text-4xl">📞</span>
        <div>
          <h1 className="text-2xl font-bold text-teal-400 tracking-wide">PRONTO</h1>
          <p className="text-slate-400 text-sm">WhatsApp Click-to-Chat</p>
        </div>
      </div>

      {/* Main Toggle Card */}
      <div className="bg-slate-800 rounded-2xl p-6 mb-4 shadow-xl">
        <div className="flex items-center gap-4">
          <span className="text-3xl">{isEnabled ? '⚡' : '💤'}</span>
          <div className="flex-1">
            <h2 className="text-white font-bold text-lg">PRONTO</h2>
            <p className={`text-sm ${isEnabled ? 'text-emerald-400' : 'text-slate-500'}`}>
              {isEnabled ? 'Attivo - In ascolto chiamate' : 'In pausa - Overlay disattivato'}
            </p>
          </div>
          <button
            onClick={() => setIsEnabled(!isEnabled)}
            className={`w-14 h-8 rounded-full transition-all duration-300 relative ${
              isEnabled ? 'bg-emerald-500' : 'bg-slate-600'
            }`}
          >
            <div
              className={`w-6 h-6 bg-white rounded-full absolute top-1 transition-all duration-300 shadow-md ${
                isEnabled ? 'left-7' : 'left-1'
              }`}
            />
          </button>
        </div>
      </div>

      {/* Permissions Card */}
      <div className="bg-slate-800 rounded-2xl p-6 mb-4 shadow-xl">
        <h3 className="text-white font-bold mb-4">Configurazione</h3>
        
        <div className="flex items-center justify-between py-3 border-b border-slate-700">
          <span className="text-slate-200">Overlay su altre app</span>
          <span className="text-lg">{overlayPermission ? '✅' : '❌'}</span>
        </div>
        
        <div className="flex items-center justify-between py-3 mb-4">
          <span className="text-slate-200">Ottimizzazione batteria</span>
          <span className="text-lg">{batteryPermission ? '✅' : '❌'}</span>
        </div>
        
        <button 
          onClick={() => setBatteryPermission(true)}
          className="w-full py-3 bg-teal-500 hover:bg-teal-600 text-white font-semibold rounded-xl transition-all"
        >
          Configura Permessi
        </button>
      </div>

      {/* Info Card */}
      <div className="bg-slate-800 rounded-2xl p-6 shadow-xl">
        <h3 className="text-white font-bold mb-3">Cos'è PRONTO?</h3>
        <p className="text-slate-400 text-sm leading-relaxed mb-4">
          Quando ricevi una chiamata, PRONTO mostra un pulsante per aprire rapidamente una chat WhatsApp con quel numero.
          <br /><br />
          Perfetto per rispondere via messaggio quando non puoi parlare.
        </p>
        <p className="text-slate-600 text-xs">Versione 1.0.0</p>
      </div>

      {/* Simulated Phone Frame */}
      <div className="mt-8 text-center text-slate-600 text-xs">
        ↑ Anteprima MainActivity (Homepage App)
      </div>
    </div>
  )
}
