package com.aengix.tvbrowser

import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Calendar

object BrandingFooter {

    fun bind(textView: TextView, onOpenUrl: (String) -> Unit) {
        val context = textView.context
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("?")
        val year = Calendar.getInstance().get(Calendar.YEAR).toString()
        val tagline = context.getString(R.string.branding_tagline)
        val companyName = context.getString(R.string.branding_company_name)
        val companyUrl = context.getString(R.string.branding_company_url)

        val creditLine = context.getString(
            R.string.branding_credit_line,
            version,
            year,
            companyName
        )
        val fullText = "$tagline\n$creditLine"
        val spannable = SpannableString(fullText)
        val linkStart = fullText.indexOf(companyName)
        if (linkStart >= 0) {
            val linkColor = ContextCompat.getColor(context, R.color.primary)
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        onOpenUrl(companyUrl)
                    }

                    override fun updateDrawState(textPaint: TextPaint) {
                        textPaint.color = linkColor
                        textPaint.isUnderlineText = true
                    }
                },
                linkStart,
                linkStart + companyName.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        textView.text = spannable
        textView.movementMethod = LinkMovementMethod.getInstance()
    }
}
