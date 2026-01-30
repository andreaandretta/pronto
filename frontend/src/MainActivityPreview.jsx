import { useState } from 'react'

export default function MainActivityPreview() {
  const [isEnabled, setIsEnabled] = useState(true)
  const [overlayPermission, setOverlayPermission] = useState(false)
  const [phonePermission, setPhonePermission] = useState(false)
  const [notificationPermission, setNotificationPermission] = useState(true)
  const [batteryPermission, setBatteryPermission] = useState(false)

  const allGranted = overlayPermission && phonePermission && notificationPermission && batteryPermission

  const PermissionRow = ({ label, subtitle, granted, onClick }) => (
    <button
      onClick={onClick}
      className="flex items-center w-full bg-slate-700 hover:bg-slate-600 rounded-xl p-3 mb-3 transition-all"
    >
      <div className="flex-1 text-left">
        <p className="text-slate-200 font-semibold text-sm">{label}</p>
        <p className="text-slate-400 text-xs">{subtitle}</p>
      </div>
      <span className="text-xl">{granted ? '✅' : '❌'}</span>
    </button>
  )

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

      {/* Permissions Card - Hidden when all granted */}
      {!allGranted && (
        <div className="bg-slate-800 rounded-2xl p-6 mb-4 shadow-xl">
          <h3 className="text-white font-bold mb-2">Autorizzazioni Richieste</h3>
          <p className="text-slate-400 text-xs mb-4">Tutte le autorizzazioni sono necessarie per funzionare</p>
          
          <PermissionRow 
            label="Overlay su altre app" 
            subtitle="Mostra pulsante durante chiamate"
            granted={overlayPermission}
            onClick={() => setOverlayPermission(true)}
          />
          
          <PermissionRow 
            label="Stato telefono" 
            subtitle="Rileva chiamate in arrivo"
            granted={phonePermission}
            onClick={() => setPhonePermission(true)}
          />
          
          <PermissionRow 
            label="Notifiche" 
            subtitle="Mostra notifica servizio attivo"
            granted={notificationPermission}
            onClick={() => setNotificationPermission(true)}
          />
          
          <PermissionRow 
            label="Ottimizzazione batteria" 
            subtitle="Funziona in background"
            granted={batteryPermission}
            onClick={() => setBatteryPermission(true)}
          />
          
          <button 
            onClick={() => {
              setOverlayPermission(true)
              setPhonePermission(true)
              setNotificationPermission(true)
              setBatteryPermission(true)
            }}
            className="w-full py-3 mt-2 bg-teal-500 hover:bg-teal-600 text-white font-semibold rounded-xl transition-all"
          >
            🔧 Configura Tutti i Permessi
          </button>
        </div>
      )}

      {/* Success Card - Shown when all granted */}
      {allGranted && (
        <div className="bg-emerald-900/30 border border-emerald-500/30 rounded-2xl p-6 mb-4 shadow-xl">
          <div className="flex items-center gap-3">
            <span className="text-2xl">✅</span>
            <div>
              <h3 className="text-emerald-400 font-bold">Tutto pronto!</h3>
              <p className="text-emerald-300/70 text-sm">Tutte le autorizzazioni sono state concesse</p>
            </div>
          </div>
        </div>
      )}

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
