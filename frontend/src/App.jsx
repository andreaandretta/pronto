export default function App() {
  const [phoneNumber, setPhoneNumber] = useState('+39 333 1234567')
  const [callerName, setCallerName] = useState('Numero Sconosciuto')

  useEffect(() => {
    // Leggi il numero dal parametro URL (passato dall'Android)
    const urlParams = new URLSearchParams(window.location.search)
    const phone = urlParams.get('phone')