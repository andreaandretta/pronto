import { useState, useEffect } from 'react'

export default function App() {
  const [num, setNum] = useState('...');
  
  useEffect(() => {
    window.updateCallerNumber = (n) => setNum(n);
  }, []);

  const action = (a) => window.AndroidBridge?.performAction(a);

  return (
    <div className="p-4 m-4 bg-white rounded-3xl shadow-2xl border-2 border-green-500 text-center">
      <h1 className="text-gray-500 text-sm font-bold uppercase">Chiamata in arrivo</h1>
      <p className="text-3xl font-black my-4 text-green-600">{num}</p>
      <div className="flex flex-col gap-2">
        <button onClick={() => action('WHATSAPP')} className="bg-green-500 text-white py-3 rounded-xl font-bold">WhatsApp</button>
        <div className="flex gap-2">
          <button onClick={() => action('REJECT')} className="flex-1 bg-red-500 text-white py-3 rounded-xl font-bold">Rifiuta</button>
          <button onClick={() => action('ANSWER')} className="flex-1 bg-blue-500 text-white py-3 rounded-xl font-bold">Rispondi</button>
        </div>
      </div>
    </div>
  );
}
