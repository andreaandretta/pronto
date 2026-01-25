import { useState, useEffect } from 'react'

const WhatsAppIcon = ({ size = 24 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
    <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z"/>
  </svg>
)

export default function App() {
  const [num, setNum] = useState('...');
  useEffect(() => {
    window.updateCallerNumber = (n) => setNum(n);
  }, []);

  const action = (a) => {
    if (window.AndroidBridge) {
      window.AndroidBridge.performAction(a);
    } else {
      console.log("Azione simulata:", a);
      if (a === 'CLOSE') setNum('...');
    }
  };

  return (
    <div className="flex flex-col items-center justify-start min-h-screen bg-transparent p-4 font-sans">
      <div className="w-full max-w-md bg-white rounded-[2.5rem] shadow-[0_20px_50px_rgba(0,0,0,0.2)] overflow-hidden border border-slate-100 relative">
        {/* Tasto Chiudi */}
        <button 
          onClick={() => action('CLOSE')} 
          className="absolute top-6 right-6 w-10 h-10 flex items-center justify-center rounded-full bg-slate-50 text-slate-400 hover:text-slate-600 transition-colors active:scale-90"
        >
          <span className="text-3xl leading-none font-light">&times;</span>
        </button>

        <div className="p-8 pt-12 flex flex-col items-center">
          <div className="w-20 h-20 bg-green-50 rounded-[1.8rem] flex items-center justify-center mb-6 shadow-sm">
            <div className="text-[#25D366]">
              <WhatsAppIcon size={44} />
            </div>
          </div>

          <h2 className="text-slate-400 text-[0.75rem] font-bold uppercase tracking-[0.3em] mb-3">Chiamata in arrivo</h2>
          <p className="text-4xl font-black text-slate-800 mb-10 tracking-tight">{num}</p>

          <div className="w-full space-y-4">
            {/* Pulsante WhatsApp Principale */}
            <button 
              onClick={() => action('WHATSAPP')} 
              className="w-full bg-[#25D366] hover:bg-[#22c35e] text-white py-5 rounded-[1.5rem] font-bold text-lg shadow-[0_12px_24px_rgba(37,211,102,0.3)] flex items-center justify-center gap-3 transition-all active:scale-[0.97]"
            >
              <WhatsAppIcon size={24} />
              <span>Messaggio WhatsApp</span>
            </button>

            {/* Pulsanti Secondari */}
            <div className="flex gap-4 pt-2">
              <button 
                onClick={() => action('REJECT')} 
                className="flex-1 bg-slate-50 hover:bg-red-50 text-slate-500 hover:text-red-500 py-4 rounded-[1.25rem] font-bold text-sm transition-all active:scale-[0.97] border border-transparent hover:border-red-100"
              >
                Rifiuta
              </button>
              <button 
                onClick={() => action('ANSWER')} 
                className="flex-1 bg-slate-50 hover:bg-blue-50 text-slate-500 hover:text-blue-500 py-4 rounded-[1.25rem] font-bold text-sm transition-all active:scale-[0.97] border border-transparent hover:border-blue-100"
              >
                Rispondi
              </button>
            </div>
          </div>
        </div>
        {/* Pillola estetica inferiore */}
        <div className="h-1.5 w-32 bg-slate-100 rounded-full mx-auto mb-6 opacity-60"></div>
      </div>
    </div>
  );
}
