    companion object {
        private const val TAG = "CallReceiver"
        private const val DEBOUNCE_MS = 500L
        
        // Race condition prevention: atomic flag for service start
        private val isStartingService = AtomicBoolean(false)
        private var lastRingingTime = 0L
        
        // BUG FIX: Track if we're waiting for real number after null first event
        private var pendingPrivateNumber = false
        private var serviceStarted = false
    }
    
    // Sanitize phone number to prevent injection
    private fun sanitizePhoneNumber(input: String?): String {