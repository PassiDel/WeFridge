package app.wefridge.parse.application.model

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import com.parse.ParseGeoPoint
import java.io.IOException
import java.util.*

class LocationController(
    private val servedFragment: Fragment,
    private val callbackOnPermissionDenied: () -> kotlin.Unit,
    private val callbackForPermissionRationale: ((Boolean) -> kotlin.Unit) -> kotlin.Unit,
    private val callbackOnDeterminationFailed: () -> kotlin.Unit,
    private val callbackOnSuccess: (geoPoint: ParseGeoPoint) -> kotlin.Unit
) {
    private val requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    init {
        // this piece of code is partially based on https://developer.android.com/training/permissions/requesting#kotlin
        requestPermissionLauncher =
            servedFragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    getCurrentLocation()
                } else {
                    callbackOnPermissionDenied()
                    Log.d("EditFragment", "Request for location access denied.")
                }
            }
    }


    fun getCurrentLocation() {
        // this piece of code is partially based on https://developer.android.com/training/permissions/requesting#kotlin
        when {
            ActivityCompat.checkSelfPermission(servedFragment.requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                // called when permission was granted
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(servedFragment.requireContext())
                fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                    .addOnSuccessListener { geoPoint ->
                        if (geoPoint != null) callbackOnSuccess(
                            ParseGeoPoint(geoPoint.latitude, geoPoint.longitude)
                        )
                        else callbackOnDeterminationFailed()

                    }

                    .addOnFailureListener { exception ->
                        callbackOnDeterminationFailed()
                        Log.e("ItemController", "Error after getCurrentLocation: ", exception)
                    }

            }
            servedFragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // show Dialog which explains the reason for accessing the user's location
                // called, when permissions denied
                callbackForPermissionRationale { shouldRequestPermission ->
                    if (shouldRequestPermission)
                        requestPermissionLauncher.launch(
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                }
            }
            else -> {
                // called when permission settings unspecified (like "ask every time")
                requestPermissionLauncher.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }
    }

    companion object {
        fun buildAddressStringFrom(geoPoint: ParseGeoPoint, ctx: Context): String {
            val geocoder = Geocoder(ctx, Locale.getDefault())
            val addressString: String
            try {
                val address =
                    geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)

                val addressLine = address[0].getAddressLine(0)
                val locality = address[0].locality
                val adminArea = address[0].adminArea
                val postalCode = address[0].postalCode

                addressString = "${addressLine}, $postalCode $locality, $adminArea"

            } catch (exc: IOException) {
                Log.e(
                    "LocationController",
                    "Error while building address string from GeoPoint: ",
                    exc
                )
                throw exc
            }

            return addressString

        }

        fun getGeoPointFrom(address: String, ctx: Context): ParseGeoPoint {
            // the following piece of code is inspired by https://stackoverflow.com/questions/3574644/how-can-i-find-the-latitude-and-longitude-from-address/27834110#27834110
            val geocoder = Geocoder(ctx)
            val matchedGeoPoint: ParseGeoPoint

            try {
                val matchedAddresses: List<Address> = geocoder.getFromLocationName(address, 1)
                val chosenAddress = matchedAddresses[0]
                matchedGeoPoint = ParseGeoPoint(chosenAddress.latitude, chosenAddress.longitude)
            } catch (exc: Exception) {
                Log.e("EditFragment", "Error while building GeoPoint from address string: ", exc)
                throw exc
            }

            return matchedGeoPoint
        }
    }
}