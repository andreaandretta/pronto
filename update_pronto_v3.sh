#!/bin/bash
# 1. Aggiornamento UI con Logo e Design Avanzato
cat << 'INNER_EOF' > frontend/src/App.jsx
import { useState, useEffect } from 'react'

const ProntoLogo = () => (
  <svg width="60" height="60" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
    <circle cx="12" cy="12" r="12" fill="#25D366"/>
    <path d="M17.5 14.5c-.3-.1-1.8-.9-2-1-.2-.1-.4-.1-.6.2-.2.3-.8 1-.9 1.2-.1.2-.3.2-.6.1-.3-.1-1.2-.4-2.3-1.4-.9-.8-1.5-1.8-1.7-2.1-.2-.3 0-.5.1-.6.1-.1.3-.3.4-.5.1-.2.2-.3.3-.5.1-.2 0-.4-.1-.5-.1-.2-.6-1.6-.9-2.2-.2-.6-.5-.5-.7-.5h-.6c-.2 0-.5.1-.8.4-.3.3-1 1-1 2.5s1.1 2.9 1.2 3.1c.1.2 2.1 3.2 5.1 4.5.7.3 1.3.5 1.7.6.7.2 1.4.2 1.9.1.6-.1 1.8-.7 2-1.4.2-.7.2-1.3.1-1.4-.1-.2-.3-.3-.6-.4z" fill="white"/>
  </svg>
)

const WhatsAppIcon = ({ size = 24 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
    <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z"/>
  </svg>
)

export default function App() {
  const [num, setNum] = useState('+39 347 123 4567');
  useEffect(() => {
    window.updateCallerNumber = (n) => setNum(n);
  }, []);

  const action = (a) => {
    if (window.AndroidBridge) {
      window.AndroidBridge.performAction(a);
    } else {
      console.log("Azione:", a);
    }
  };

  return (
    <div className="flex flex-col items-center justify-start min-h-screen bg-transparent p-6 font-sans">
      <div className="w-full max-w-md bg-white rounded-[3rem] shadow-[0_30px_60px_rgba(0,0,0,0.12)] overflow-hidden border border-slate-50 relative">
        
        {/* Tasto Chiudi */}
        <button 
          onClick={() => action('CLOSE')} 
          className="absolute top-8 right-8 w-10 h-10 flex items-center justify-center rounded-full bg-slate-50 text-slate-300 hover:text-slate-500 transition-all active:scale-90"
        >
          <span className="text-3xl leading-none font-light">&times;</span>
        </button>

        <div className="p-10 pt-14 flex flex-col items-center">
          {/* LOGO PRONTO */}
          <div className="mb-8 transform hover:scale-105 transition-transform">
            <ProntoLogo />
          </div>

          <h2 className="text-slate-400 text-[0.7rem] font-black uppercase tracking-[0.3em] mb-3">Chiamata in arrivo</h2>
          <p className="text-4xl font-black text-slate-800 mb-12 tracking-tighter">{num}</p>

          <div className="w-full space-y-4">
            {/* Pulsante WhatsApp - Il cuore dell'app */}
            <button 
              onClick={() => action('WHATSAPP')} 
              className="w-full bg-[#25D366] hover:bg-[#20ba5a] text-white py-6 rounded-[2rem] font-bold text-xl shadow-[0_15px_30px_rgba(37,211,102,0.3)] flex items-center justify-center gap-4 transition-all active:scale-[0.96]"
            >
              <WhatsAppIcon size={28} />
              <span>WhatsApp Subito</span>
            </button>

            {/* Pulsanti Secondari */}
            <div className="flex gap-4 pt-4">
              <button 
                onClick={() => action('REJECT')} 
                className="flex-1 bg-slate-50 hover:bg-red-50 text-slate-400 hover:text-red-500 py-5 rounded-[1.5rem] font-bold text-sm transition-all active:scale-[0.96]"
              >
                Rifiuta
              </button>
              <button 
                onClick={() => action('ANSWER')} 
                className="flex-1 bg-slate-50 hover:bg-blue-50 text-slate-400 hover:text-blue-500 py-5 rounded-[1.5rem] font-bold text-sm transition-all active:scale-[0.96]"
              >
                Rispondi
              </button>
            </div>
          </div>
        </div>
        
        {/* Barra estetica stile iPhone */}
        <div className="h-1.5 w-28 bg-slate-100 rounded-full mx-auto mb-6"></div>
      </div>
    </div>
  );
}
INNER_EOF

# 2. Pulizia e Riavvio
echo "🧹 Pulizia cache e riavvio..."
rm -rf frontend/dist
cd frontend && npm run build
mkdir -p ../android/app/src/main/assets/www
cp -r dist/* ../android/app/src/main/assets/www/
cd ..

echo "✅ Fatto! Controlla ora l'URL Cloudflare o GitHub Actions."
