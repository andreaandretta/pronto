import { useState, useEffect } from 'react'

export default function App() {
  const [phoneNumber, setPhoneNumber] = useState('+39 333 1234567')

  useEffect(() => {
    // Leggi il numero dal parametro URL (passato dall'Android)
    const urlParams = new URLSearchParams(window.location.search)
    const phone = urlParams.get('phone')
    if (phone) {
      setPhoneNumber(decodeURIComponent(phone))
    }

    // Bridge Android -> React
    window.receivePhoneNumber = (number) => {
      console.log('Received phone number from Android:', number)
      if (number && typeof number === 'string') {
        setPhoneNumber(number)
      }
    }

    // Notifica Android che React è pronto
    if (window.Android?.onReactReady) {
      window.Android.onReactReady()
    }

    return () => {
      delete window.receivePhoneNumber
    }
  }, [])

  const openWhatsApp = () => {
    // Pulisci il numero per WhatsApp (solo cifre)
    let cleanNumber = phoneNumber.replace(/[\s\-\(\)]/g, '')
    
    // Gestisci prefisso italiano
    if (cleanNumber.startsWith('+39')) {
      cleanNumber = '39' + cleanNumber.slice(3)
    } else if (cleanNumber.startsWith('39')) {
      // già OK
    } else if (cleanNumber.startsWith('+')) {
      cleanNumber = cleanNumber.slice(1)
    } else if (/^3\d{9}$/.test(cleanNumber)) {
      // Numero italiano senza prefisso
      cleanNumber = '39' + cleanNumber
    }

    const waUrl = `https://wa.me/${cleanNumber}`
    console.log('Opening WhatsApp:', waUrl)

    // Prova il bridge Android, altrimenti apri direttamente
    if (window.Android?.openWhatsApp) {
      window.Android.openWhatsApp(cleanNumber)
    } else {
      window.open(waUrl, '_blank')
    }
  }

  const closeOverlay = () => {
    if (window.Android?.closeOverlay) {
      window.Android.closeOverlay()
    } else {
      console.log('Close overlay (Android bridge not available)')
    }
  }

  return (
    <div className="min-h-screen bg-transparent flex items-start justify-center pt-4 px-4">
      {/* Card Overlay */}
      <div className="w-full max-w-sm bg-slate-900/95 backdrop-blur-xl rounded-2xl shadow-2xl border border-slate-700/50 overflow-hidden">
        
        {/* Header */}
        <div className="bg-gradient-to-r from-teal-600 to-teal-500 px-4 py-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-xl">📞</span>
              <span className="text-white font-bold text-lg tracking-wide">PRONTO</span>
            </div>
            <button 
              onClick={closeOverlay}
              className="text-white/80 hover:text-white text-xl font-bold w-8 h-8 flex items-center justify-center rounded-full hover:bg-white/20 transition-all"
            >
              ×
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="p-4 space-y-3">
          {/* WhatsApp Button */}
          <button
            onClick={openWhatsApp}
            className="w-full py-3 bg-green-500 hover:bg-green-600 active:bg-green-700 text-white font-bold rounded-xl flex items-center justify-center gap-3 transition-all shadow-lg"
          >
            <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
              <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z"/>
            </svg>
            Apri WhatsApp
          </button>

          {/* Close Button */}
          <button
            onClick={closeOverlay}
            className="w-full py-2 bg-slate-700 hover:bg-slate-600 text-slate-300 font-medium rounded-xl transition-all"
          >
            Chiudi
          </button>
        </div>
      </div>
    </div>
  )
}
