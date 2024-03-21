package vac.test.bluetoothbledemo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.TextUtils
import androidx.annotation.RequiresApi
import com.google.protobuf.Timestamp
import java.security.MessageDigest
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

object CommonUtil {

    fun clipboardText(context: Context,textToCopy :String){
        // 获取剪贴板管理器的实例
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // 创建ClipData对象
        val clip = ClipData.newPlainText("label", textToCopy)
        // 将ClipData对象设置到剪贴板上
        clipboard.setPrimaryClip(clip)
    }

    fun parseToFloat(input: String): Double {
        val pattern = Pattern.compile("-?\\d+\\.\\d+")
        val matcher = pattern.matcher(input)

        return if (matcher.find()) {
            val floatValue = matcher.group().toDouble()
            floatValue
        } else {
            0.0
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun formatTimestamp2(timestamp: Timestamp): String {
        val seconds = timestamp.seconds
        val nanos = timestamp.nanos
        val instant = Instant.ofEpochSecond(seconds, nanos.toLong())
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return localDateTime.format(formatter)
    }

    fun parseToDouble(input: String): Double {
        return try {
            if (TextUtils.isEmpty(input) || TextUtils.isEmpty(input.trim())){
                0.0
            }else{
                input.toDouble()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0.0
        }
    }

    fun formattedValue(floatValue : Double): String{
        val decimalFormat = DecimalFormat("0.######")
        return decimalFormat.format(floatValue)
    }

    fun formatTimestamp(timestamp: Timestamp): String {
       /* if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            return formatTimestamp2(timestamp)
        }*/

        val seconds = timestamp.seconds
        val nanos = timestamp.nanos

        val timeInMillis = seconds * 1000 + nanos / 1000000 // 将秒和纳秒转换为毫秒

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateTime = Calendar.getInstance()
        dateTime.timeInMillis = timeInMillis

        return dateFormat.format(dateTime.time)

        /*val seconds = timestamp.seconds
        val nanos = timestamp.nanos

        val timeInMillis = seconds * 1000 + nanos / 1000000 // 将秒和纳秒转换为毫秒

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateTime = Calendar.getInstance()
        dateTime.timeInMillis = timeInMillis

        return dateFormat.format(dateTime.time)*/
    }

    fun calculateMD5(data: ByteArray): String {
        val md5Digest = MessageDigest.getInstance("MD5")
        val md5Hash = md5Digest.digest(data)
        return bytesToHex(md5Hash)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }


}