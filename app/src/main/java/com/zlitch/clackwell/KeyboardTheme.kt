package com.zlitch.clackwell

class KeyboardTheme(
    val title: String,
    val bgTop: Int, val bgBottom: Int,
    val faceLight: Int, val faceDark: Int,
    val rimLight: Int, val rimMid: Int, val rimDark: Int,
    val ink: Int, val ring: Int,
    val rod: Int, val rodShine: Int,
    val toolbar: Int, val accent: Int, val dark: Boolean
) {
    companion object {
        private fun c(v: Long) = v.toInt()
        val ALL = listOf(
            KeyboardTheme("Ivory", c(0xFFF4F0E6), c(0xFFD9D3C5), c(0xFFFFFDF4), c(0xFFEDE5CF),
                c(0xFFFFFFFF), c(0xFFBFC1C4), c(0xFF6E7176), c(0xFF2B2A28), c(0xFF4A4741),
                c(0xFF34332F), c(0xFFC9C8C2), c(0xFFF7F3EA), c(0xFFA8761F), false),
            KeyboardTheme("White and silver", c(0xFFDBDDE2), c(0xFFC6C8CD), c(0xFFEEF0F3), c(0xFFDFE1E6),
                c(0xFFE4E6EA), c(0xFF96999F), c(0xFF55585E), c(0xFF26272B), c(0xFF6A6D73),
                c(0xFF2B2D32), c(0xFF8B8E95), c(0xFFDBDDE2), c(0xFF3E6D8E), false),
            KeyboardTheme("Dark vintage", c(0xFF39332C), c(0xFF191613), c(0xFFF3EAD3), c(0xFFD9CBA8),
                c(0xFFF1E6C8), c(0xFFA8946A), c(0xFF4E422C), c(0xFF2A231A), c(0xFF4A3D28),
                c(0xFF0C0B0A), c(0xFF8A7B5C), c(0xFF2C2722), c(0xFFE0B45C), true),
            KeyboardTheme("Black chrome", c(0xFF25272A), c(0xFF0D0E10), c(0xFF34363A), c(0xFF141518),
                c(0xFFF4F6F8), c(0xFF9AA0A8), c(0xFF3A3E44), c(0xFFF2F2EE), c(0xFF050506),
                c(0xFF000000), c(0xFF6C727A), c(0xFF191A1D), c(0xFF8FC3E8), true),
            KeyboardTheme("Warm beige", c(0xFFE6D5B8), c(0xFFC4AD8A), c(0xFFFBF1DC), c(0xFFE8D6B4),
                c(0xFFFFF8EA), c(0xFFC2B8A6), c(0xFF776A55), c(0xFF3A2D1F), c(0xFF5A4A36),
                c(0xFF4A3B2A), c(0xFFD8C8AC), c(0xFFEBDCC2), c(0xFF8A4B22), false))
    }
}
