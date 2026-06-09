package com.aengix.tvbrowser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aengix.tvbrowser.databinding.FragmentSettingsBinding
class SettingsFragment : Fragment() {

    companion object {
        private const val REQUEST_CLEAR_DATA = "clear_data"

        fun newInstance(): SettingsFragment = SettingsFragment()
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        parentFragmentManager.setFragmentResultListener(
            REQUEST_CLEAR_DATA,
            viewLifecycleOwner
        ) { _, bundle ->
            if (bundle.getBoolean(ConfirmDialogFragment.RESULT_CONFIRMED)) {
                BrowsingDataCleaner.clear(requireContext())
                Toast.makeText(
                    requireContext(),
                    R.string.clear_browsing_data_done,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.buttonClearData.setOnClickListener {
            ConfirmDialogFragment.show(
                this,
                REQUEST_CLEAR_DATA,
                getString(R.string.clear_browsing_data_confirm)
            )
        }

        setupCursorSpeedControls()

        BrandingFooter.bind(binding.brandingFooter.textBrandingFooter) { url ->
            (requireActivity() as MainActivity).openBrowser(url)
        }
    }

    private fun setupCursorSpeedControls() {
        val activity = requireActivity() as MainActivity
        val selectedId = when (activity.cursorSpeed()) {
            CursorSpeedStore.Speed.HALF -> R.id.button_speed_half
            CursorSpeedStore.Speed.NORMAL -> R.id.button_speed_normal
            CursorSpeedStore.Speed.DOUBLE -> R.id.button_speed_double
        }
        binding.toggleCursorSpeed.check(selectedId)

        binding.toggleCursorSpeed.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val speed = when (checkedId) {
                R.id.button_speed_half -> CursorSpeedStore.Speed.HALF
                R.id.button_speed_double -> CursorSpeedStore.Speed.DOUBLE
                else -> CursorSpeedStore.Speed.NORMAL
            }
            activity.setCursorSpeed(speed)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
