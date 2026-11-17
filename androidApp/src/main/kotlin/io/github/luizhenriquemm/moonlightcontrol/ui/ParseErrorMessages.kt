package io.github.luizhenriquemm.moonlightcontrol.ui

import androidx.annotation.StringRes
import io.github.luizhenriquemm.moonlightcontrol.R
import io.github.luizhenriquemm.moonlightcontrol.shared.ParseError

/** User-facing message for an address that could not be parsed. */
@StringRes
fun ParseError.messageRes(): Int = when (this) {
    ParseError.EMPTY -> R.string.connect_error_empty
    ParseError.UNSUPPORTED_SCHEME -> R.string.connect_error_scheme
    ParseError.INVALID_HOST -> R.string.connect_error_host
    ParseError.INVALID_PORT -> R.string.connect_error_port
}
