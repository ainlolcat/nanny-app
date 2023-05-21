package org.ainlolcat.nanny

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import org.ainlolcat.nanny.R
import org.ainlolcat.nanny.databinding.FragmentSettingsBinding
import org.ainlolcat.nanny.settings.NannySettings
import com.google.android.material.snackbar.Snackbar
import java.util.stream.Collectors

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val preferences = context!!.getSharedPreferences("org.ainlolcat.nanny.app",
            AppCompatActivity.MODE_PRIVATE
        )
        val nannySettings = NannySettings(preferences)
        Log.i("SettingsFragment", "Current settings: $nannySettings")

        if (nannySettings.botToken != null) {
            binding.settingBotSecret.text = SpannableStringBuilder(nannySettings.botToken)
        }
        binding.settingAllowedUsers.text = SpannableStringBuilder(nannySettings.allowedUsers.stream().collect(Collectors.joining(";")))
        binding.settingSoundLevelThreshold.text = SpannableStringBuilder(nannySettings.soundLevelThreshold.toString())

        binding.buttonSaveSettings.setOnClickListener {
            val newBotToken = binding.settingBotSecret.text.toString()
            val newAllowedUsers = ArrayList(binding.settingAllowedUsers.text.toString().split(";"))
            val newThresholdText = binding.settingSoundLevelThreshold.text.toString()
            var newThreshold = nannySettings.soundLevelThreshold
            if (newThresholdText.matches("^[0-9]+$".toRegex())) {
                newThreshold = Integer.parseInt(newThresholdText)
            } else {
                Snackbar.make(view, "Sound level threshold should be a number", Snackbar.LENGTH_LONG).setAction("Action", null).show()
            }
            val newKnownChats = nannySettings.knownChats.entries.stream().filter { e -> newAllowedUsers.contains(e.key) }.collect(Collectors.toMap({ x -> x.key},{ x -> x.value}))
            val newNannySettings = NannySettings(newBotToken, newAllowedUsers, newKnownChats, newThreshold)

            newNannySettings.storeSettings(preferences)
            Log.i("SettingsFragment", "New settings: $newNannySettings")

            if (context is MainActivity) {
                (context as MainActivity).initAndApplySettings()
            }

            findNavController().navigate(R.id.action_SettingsFragment_to_InitialFragment)
        }

        binding.buttonDiscardSettings.setOnClickListener {
            findNavController().navigate(R.id.action_SettingsFragment_to_InitialFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}