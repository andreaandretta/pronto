import { useState, useEffect, useRef } from 'react'

export default function App() {
  const [phoneNumber, setPhoneNumber] = useState('+39 333 1234567')
  const [callerName, setCallerName] = useState('Numero Sconosciuto')
  const [isReady, setIsReady] = useState(false)
  const reactReadySent = useRef(false)

  useEffect(() => {
    console.log('PRONTO: App mounted, setting up bridge...')
    
    // Leggi il numero dal parametro URL (passato dall'Android)
    const urlParams = new URLSearchParams(window.location.search)
    const phone = urlParams.get('phone')
    if (phone) {
      setPhoneNumber(decodeURIComponent(phone))
    }

    // Esponi le funzioni globalmente per Android Bridge
    window.setPhoneNumber = (number) => {
      console.log('PRONTO: setPhoneNumber called with:', number)
      setPhoneNumber(number)
    }
    
    window.setCallerName = (name) => {
      console.log('PRONTO: setCallerName called with:', name)
      setCallerName(name)
    }
    
    window.updateCallerInfo = (number, name) => {
      console.log('PRONTO: updateCallerInfo called:', number, name)
      if (number) setPhoneNumber(number)
      if (name) setCallerName(name)
    }
    
    // Prova a ottenere il numero dal bridge Android se disponibile
    if (window.Android && window.Android.getPhoneNumber) {
      try {
        const num = window.Android.getPhoneNumber()
        if (num) setPhoneNumber(num)
      } catch (e) {
        console.log('PRONTO: Could not get phone number from Android bridge')
      }
    }
    
    // Segnala a Kotlin che React è pronto (HANDSHAKE)
    // Usa un ref per evitare invii multipli
    if (!reactReadySent.current) {
      reactReadySent.current = true
      
      // Piccolo delay per assicurarsi che tutto sia renderizzato
      const timer = setTimeout(() => {
        if (window.Android && window.Android.onReactReady) {
          try {
            window.Android.onReactReady()
            console.log('PRONTO: Sent onReactReady to Android')
          } catch (e) {
            console.error('PRONTO: Error sending onReactReady:', e)
          }
        } else {
          console.log('PRONTO: Android bridge not available (browser mode)')
        }
        
        // Segna come pronto e nascondi loading
        setIsReady(true)
        if (window.hideLoading) {
          window.hideLoading()
        }
      }, 100)
      
      return () => {
        clearTimeout(timer)
        console.log('PRONTO: App unmounting')
      }
    }
  }, [])

  const action = (type) => {
    console.log('PRONTO: Action:', type)
    // Chiama il bridge Android se disponibile
    if (window.Android && window.Android.performAction) {
      try {
        window.Android.performAction(type)
      } catch (e) {
        console.error('PRONTO: Android bridge error:', e)
      }
    } else {
      console.log('PRONTO: Android bridge not available - browser mode')
      // In browser mode, mostra un alert
      alert('Azione: ' + type + ' per ' + phoneNumber)
    }
  }

  // Se non ancora pronto, mostra nulla (la schermata di caricamento HTML gestisce tutto)
  if (!isReady) {
    return null
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-teal-700 via-teal-600 to-emerald-500 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        {/* Card principale */}
        <div className="bg-white rounded-[2.5rem] shadow-2xl overflow-hidden animate-fadeIn">
          {/* Header con icona */}
          <div className="bg-gradient-to-br from-teal-800 to-teal-700 pt-8 pb-12 px-6 text-center">
            <div className="w-20 h-20 mx-auto bg-gray-500/30 rounded-full flex items-center justify-center mb-4">
              <svg className="w-10 h-10 text-pink-400" fill="currentColor" viewBox="0 0 24 24">
                <path d="M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z"/>
              </svg>
            </div>
            <p className="text-teal-300 text-sm mb-1">Chiamata in arrivo</p>
            <h1 className="text-white text-2xl font-bold mb-1">{callerName}</h1>
            <p className="text-teal-200">{phoneNumber}</p>
          </div>

          {/* Pulsanti azione */}
          <div className="p-6 space-y-3">
            {/* WhatsApp Button */}
            <button
              onClick={() => action('WHATSAPP')}
              className="w-full bg-[#25D366] hover:bg-[#20bd5a] text-white py-4 rounded-2xl font-bold text-lg flex items-center justify-center gap-3 transition-all active:scale-[0.98] shadow-lg"
            >
              <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
                <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z"/>
              </svg>
              Apri Chat WhatsApp
            </button>

            {/* Rispondi e Rifiuta */}
            <div className="flex gap-3">
              <button
                onClick={() => action('ANSWER')}
                className="flex-1 bg-slate-50 hover:bg-blue-50 text-slate-600 hover:text-blue-600 py-4 rounded-2xl font-bold text-sm transition-all active:scale-[0.96] flex items-center justify-center gap-2"
              >
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z"/>
                </svg>
                Rispondi
              </button>
              <button
                onClick={() => action('REJECT')}
                className="flex-1 bg-red-500 hover:bg-red-600 text-white py-4 rounded-2xl font-bold text-sm transition-all active:scale-[0.96] flex items-center justify-center gap-2"
              >
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 9c-1.6 0-3.15.25-4.6.72v3.1c0 .39-.23.74-.56.9-.98.49-1.87 1.12-2.66 1.85-.18.18-.43.28-.7.28-.28 0-.53-.11-.71-.29L.29 13.08c-.18-.17-.29-.42-.29-.7 0-.28.11-.53.29-.71C3.34 8.78 7.46 7 12 7s8.66 1.78 11.71 4.67c.18.18.29.43.29.71 0 .28-.11.53-.29.71l-2.48 2.48c-.18.18-.43.29-.71.29-.27 0-.52-.11-.7-.28-.79-.74-1.69-1.36-2.67-1.85-.33-.16-.56-.5-.56-.9v-3.1C15.15 9.25 13.6 9 12 9z"/>
                </svg>
                Rifiuta
              </button>
            </div>

            {/* Chiudi */}
            <button
              onClick={() => action('CLOSE')}
              className="w-full bg-slate-100 hover:bg-slate-200 text-slate-500 py-3 rounded-2xl font-medium text-sm transition-all"
            >
              Chiudi
            </button>
          </div>
        </div>

        {/* Footer */}
        <p className="text-center text-teal-200/60 text-xs mt-4">
          PRONTO • WhatsApp Click-to-Chat
        </p>
      </div>
    </div>
  )
}
