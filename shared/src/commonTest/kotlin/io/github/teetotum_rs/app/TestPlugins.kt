package io.github.teetotum_rs.app

import kotlin.io.encoding.Base64

/** A real plugin module and its catalogue entry, shared by the tests. */
internal object TestPlugins {
    /** The HID remote from the Knob's firmware 0.1.0, as the catalogue lists it; its header is teetotum-pack's. */
    val remote = Base64.decode(
        "AGFzbQEAAAABLwdgB39/f39/f38AYAR/f39/AGABfwBgBn9/f39/fwBgAABgBX9/f39/AGABfwF/AlUFA2VudgZtZW1vcnkCAQEB" +
            "CHRlZXRvdHVtA2FyYwAACHRlZXRvdHVtBGljb24AAQh0ZWV0b3R1bQpzZW5kX3VzYWdlAAIIdGVldG90dW0EdGV4dAADAwQDBAUG" +
            "BgcBfwFBgCALBxMCBGRyYXcABAhvbl9ldmVudAAGCt4DA80CAQN/QcuhgIAAIQBBCiEBAkBBACgCiKOAgAAiAkEvRg0AAkACQAJA" +
            "AkACQCACQc1+ag4EAQIDBAALIAJBzQFHDQRBwaGAgAAhAAwEC0GjoYCAACEAQQwhAQwDC0GvoYCAACEAQQYhAQwCC0G1oYCAACEA" +
            "QQQhAQwBC0G5oYCAACEAQQghAQtBtAFBtAFBqgFBhwFBjgJBCEGAgAQQgICAgABB2KGAgABBtAFB9gBBg4AEEIGAgIAAIAAgAUGq" +
            "AUECQYSABBCFgICAAEG4ooCAAEEUQdQBQQBBhoAEEIWAgIAAQcyigIAAQQ1B5gFBAEGGgAQQhYCAgABB2aKAgABBDEH4AUEAQYaA" +
            "BBCFgICAAAJAQQAtAIyjgIAADQBB5aKAgABBE0GKAkEAQYGABBCFgICAAA8LQfiigIAAQQ9BigJBAEGFgAQQhYCAgAALFQAgACAB" +
            "QbQBIAIgAyAEEIOAgIAAC3cBAn9BzQEhAUEAIQICQAJAAkACQAJAAkACQAJAIAAOCgUEAAECBwcHAwMHC0G1ASEBDAQLQbMBIQEM" +
            "AwtBtAEhAQwCC0EAIABBCEY6AIyjgIAADAILQbYBIQELIAEQgoCAgABBACABNgKIo4CAAAtBASECCyACCwugAwMAQYAgC4cDAwMA" +
            "AAAKSElEIHJlbW90ZQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAJwDEACcAxgAnAMeAJwDHwCcwx8AnOMfAJzzHwCc8x8A" +
            "nOMfAJzDHwCcAx8AnAMeAJwDGACcAxAAAAAAAAAAAAAAAAAAAAAAAAAAAAAdcmVtb3RlIGZvciB0aGUgcGhvbmUncyBwbGF5ZXIA" +
            "AAABAAAAAAAAAEZhc3QgZm9yd2FyZFJld2luZE5leHRQcmV2aW91c1BsYXkvUGF1c2VISUQgcmVtb3RlAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAACcAxAAnAMYAJwDHgCcAx8AnMMfAJzjHwCc8x8AnPMfAJzjHwCcwx8AnAMfAJwDHgCcAxgAnAMQAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAdGFwIHRvIHBsYXkgb3IgcGF1c2Vzd2lwZSB0byBza2lwdHVybiB0byBzZWVrcGFpciBUQUlKSV9LTk9CX0hJRHBo" +
            "b25lIGNvbm5lY3RlZABBiCMLBC8AAAAAQYwjCwEAALUBEXRlZXRvdHVtLm1hbmlmZXN0AwMAAAAKSElEIHJlbW90ZQAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAJwDEACcAxgAnAMeAJwDHwCcwx8AnOMfAJzzHwCc8x8AnOMfAJzDHwCcAx8AnAMeAJwDGACc" +
            "AxAAAAAAAAAAAAAAAAAAAAAAAAAAAAAdcmVtb3RlIGZvciB0aGUgcGhvbmUncyBwbGF5ZXIAAAABAAAAAAAAAABzEnRlZXRvdHVt" +
            "LnNpZ25hdHVyZfasqF7cNUfU4uNZFo9bodFdPF6IbSsqRDAWl+igq6q6hibr57IbLMCf8iQELK6h1qOUe528f71IxadoQ1tZPmQn" +
            "3ixDoBzTkbyUHATJm9hl+37vjiOOukLF7lkpc0K6Aw==",
    )

    val entry = CataloguePlugin(
        name = "HID remote",
        summary = "remote for the phone's player",
        version = "0.0.0",
        rights = listOf("HID", "KNOB"),
        id = "41a9ad2d2290788d",
        key = "f6aca85edc3547d4e2e359168f5ba1d15d3c5e886d2b2a44301697e8a0abaaba",
        size = 1381,
        sha256 = "ce9885d8ad7125815d665bc50088b2f461a57cd3a6b40129472417f7e6e66c0c",
        url = "https://example.invalid/hid-remote.wasm",
    )
}
