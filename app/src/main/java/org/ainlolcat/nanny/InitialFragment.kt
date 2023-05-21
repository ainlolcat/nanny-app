package org.ainlolcat.nanny

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.ainlolcat.nanny.R
import org.ainlolcat.nanny.databinding.FragmentInitialBinding


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class InitialFragment : Fragment() {

    private var _binding: FragmentInitialBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentInitialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i("InitialFragment", "onViewCreated")
        binding.buttonNavSettings.setOnClickListener {
            if (context is MainActivity) {
                (context as MainActivity).stopDetectionServiceIfRunning(binding.buttonStartInitial, view)
            }
            findNavController().navigate(R.id.action_InitialFragment_to_SettingsFragment)
        }
        if (context is MainActivity) {
            (context as MainActivity).initNannySwitch(
                view, context as MainActivity,
                binding.buttonStartInitial,
                binding.textviewAvg, binding.textviewMax)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}