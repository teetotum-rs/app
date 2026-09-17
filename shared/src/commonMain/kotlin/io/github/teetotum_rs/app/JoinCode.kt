package io.github.teetotum_rs.app

/** The network a Knob offers while its "Card over Wi-Fi" dialog is open, read from the QR code. */
data class JoinCode(val ssid: String, val password: String) {
    companion object {
        /**
         * Reads a `WIFI:T:WPA;S:<ssid>;P:<password>;;` text, where `\` escapes `\ ; , : "`.
         * Returns null for anything that is not a WPA network with a name and a password.
         */
        fun parse(text: String): JoinCode? {
            if (!text.startsWith("WIFI:")) return null
            val fields = mutableMapOf<String, String>()
            for (field in splitUnescaped(text.removePrefix("WIFI:"))) {
                val colon = field.indexOf(':')
                if (colon <= 0) continue
                fields[field.substring(0, colon)] = unescape(field.substring(colon + 1))
            }
            if (fields["T"] != "WPA") return null
            val ssid = fields["S"]?.takeIf { it.isNotEmpty() } ?: return null
            val password = fields["P"]?.takeIf { it.isNotEmpty() } ?: return null
            return JoinCode(ssid, password)
        }

        private fun splitUnescaped(text: String): List<String> {
            val fields = mutableListOf<String>()
            val current = StringBuilder()
            var escaped = false
            for (c in text) {
                when {
                    escaped -> {
                        current.append('\\').append(c)
                        escaped = false
                    }

                    c == '\\' -> escaped = true

                    c == ';' -> {
                        fields += current.toString()
                        current.clear()
                    }

                    else -> current.append(c)
                }
            }
            if (current.isNotEmpty()) fields += current.toString()
            return fields
        }

        private fun unescape(text: String): String {
            val out = StringBuilder()
            var escaped = false
            for (c in text) {
                if (!escaped && c == '\\') {
                    escaped = true
                } else {
                    out.append(c)
                    escaped = false
                }
            }
            return out.toString()
        }
    }
}
