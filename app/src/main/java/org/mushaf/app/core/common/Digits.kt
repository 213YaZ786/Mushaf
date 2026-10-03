package org.mushaf.app.core.common

/** Any digit typed (٢٥٥, ۲۵۵, ২৫৫...) as its Latin digit, the rest unchanged. */
fun latinDigits(text: String): String = buildString(text.length) {
    for (c in text) append(if (Character.isDigit(c)) '0' + Character.digit(c, 10) else c)
}
