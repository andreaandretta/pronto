# PRONTO v1.0.0 BETA - Release Notes

## Stato
**Data**: 2026-01-31  
**Commit**: f698814  
**Tag**: v1.0.0-beta  

## Funziona ✅
- Overlay appare durante chiamate in entrata
- Layout non intrusivo (sotto tasti nativi)
- Battery Optimization automatico per Samsung/Xiaomi/Huawei
- WebView Pool (cold start <150ms)
- FSM (Finite State Machine) per gestione stati
- WhatsApp integration (wa.me)
- No memory leak rilevati (NetworkCallback singleton, Handler safe)

## Limitazioni Note ⚠️
- **Carrier Sync**: Quando riagganci, il telefono chiamante continua a squillare per 5-10s (limite rete cellulare, non risolvibile)
- **Dual SIM**: Non testato su dispositivi dual SIM
- **Android < 10**: Non supportato (minSdk 26+)

## Stack Tecnico
- Kotlin 1.9, API 34
- React 18 + Vite + Tailwind
- ForegroundService (TYPE_SPECIAL_USE)
- WindowManager (TYPE_APPLICATION_OVERLAY)

## Prossimi Step Futuri (Backlog)
- [ ] CallScreeningService (opzionale, per controllo totale chiamate)
- [ ] Animazioni CSS spring polish
- [ ] Dark mode sync ottimizzato
- [ ] Post-call notification (retention)

## Commits di riferimento
- Base stabile: 08f7e64 (run 21534707880)
- Battery fix: f698814 (recovery)

## Installazione
```bash
# Da GitHub Releases
adb install -r PRONTO-v1.0.0-beta.apk
```

## Note di Sicurezza
⚠️ **Non usare in produzione senza testing approfondito** su target device specifico.
Il comportamento può variare tra OEM diversi (Samsung OneUI, Xiaomi MIUI, etc).
