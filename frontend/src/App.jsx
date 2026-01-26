import { useState, useEffect } from 'react'

const WhatsAppIcon = () => (
  <svg viewBox="0 0 24 24" className="w-6 h-6 fill-white">
    <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z"/>
  </svg>
)

export default function App() {
  const [caller, setCaller] = useState({ number: '+39 333 1234567', name: 'Numero Sconosciuto' })

  useEffect(() => {
    window.updateCallerInfo = (number, name) => {
      setCaller({ number: number || '+39 000 0000000', name: name || 'Numero Sconosciuto' })
    }
  }, [])

  const action = (a) => {
    if (window.AndroidBridge) {
      window.AndroidBridge.performAction(a)
    } else {
      console.log('Action:', a)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#075E54] via-[#128C7E] to-[#25D366] flex items-center justify-center p-4">
      <div className="bg-white rounded-3xl shadow-2xl w-full max-w-sm overflow-hidden">
        {/* Header */}
        <div className="bg-[#075E54] text-white p-6 text-center">
          <div className="w-20 h-20 bg-white/20 rounded-full flex items-center justify-center mx-auto mb-4">
            <span className="text-4xl">📞</span>
          </div>
          <p className="text-[#25D366] text-sm font-medium mb-1">Chiamata in arrivo</p>
          <h1 className="text-2xl font-bold">{caller.name}</h1>
          <p className="text-white/80 text-lg mt-1">{caller.number}</p>
        </div>

        {/* Actions */}
        <div className="p-6 space-y-3">
          {/* WhatsApp Button */}
          <button 
            onClick={() => action('whatsapp')}
            className="w-full bg-[#25D366] hover:bg-[#128C7E] text-white font-bold py-4 px-6 rounded-2xl flex items-center justify-center gap-3 transition-all transform hover:scale-[1.02] shadow-lg"
          >
            <WhatsAppIcon />
            <span className="text-lg">Apri Chat WhatsApp</span>
          </button>

          {/* Answer & Reject Row */}
          <div className="flex gap-3">
            <button 
              onClick={() => action('answer')}
              className="flex-1 bg-green-500 hover:bg-green-600 text-white font-bold py-4 rounded-2xl flex items-center justify-center gap-2 transition-all"
            >
              <span className="text-xl">📱</span>
              <span>Rispondi</span>
            </button>
            <button 
              onClick={() => action('reject')}
              className="flex-1 bg-red-500 hover:bg-red-600 text-white font-bold py-4 rounded-2xl flex items-center justify-center gap-2 transition-all"
            >
              <span className="text-xl">📵</span>
              <span>Rifiuta</span>
            </button>
          </div>

          {/* Close Button */}
          <button 
            onClick={() => action('close')}
            className="w-full bg-gray-100 hover:bg-gray-200 text-gray-700 font-medium py-3 px-6 rounded-2xl transition-all"
          >
            Chiudi
          </button>
        </div>

        {/* Footer */}
        <div className="bg-gray-50 px-6 py-4 text-center">
          <p className="text-gray-400 text-xs">PRONTO • WhatsApp Click-to-Chat</p>
        </div>
      </div>
    </div>
  )
}
