package com.aengix.tvbrowser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.aengix.tvbrowser.databinding.DialogConfirmBinding

class ConfirmDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogConfirmBinding.inflate(inflater, container, false)
        binding.textMessage.text = requireArguments().getString(ARG_MESSAGE).orEmpty()
        binding.buttonCancel.setOnClickListener { dismiss() }
        binding.buttonConfirm.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                requireArguments().getString(ARG_REQUEST_KEY).orEmpty(),
                bundleOf(RESULT_CONFIRMED to true)
            )
            dismiss()
        }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.55).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    companion object {
        const val RESULT_CONFIRMED = "confirmed"

        private const val ARG_MESSAGE = "message"
        private const val ARG_REQUEST_KEY = "request_key"

        fun show(host: Fragment, requestKey: String, message: String) {
            ConfirmDialogFragment().apply {
                arguments = bundleOf(
                    ARG_MESSAGE to message,
                    ARG_REQUEST_KEY to requestKey
                )
            }.show(host.parentFragmentManager, "confirm_$requestKey")
        }
    }
}
