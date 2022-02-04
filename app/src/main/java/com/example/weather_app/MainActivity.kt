package com.example.weather_app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.weather_app.network.WeatherService
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.weatherapp.models.WeatherResponse
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {
    private lateinit var _fusedLocationClient:  FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        _fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if(!isLocatinEnabled()){
            Toast.makeText(this@MainActivity, "Your location is turned off", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            /*
            * Dexter runtime'da izinleri basit bir şekilde işleyen bir android kütüphanesi
            * */
            Dexter.withActivity(this).withPermissions(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object: MultiplePermissionsListener{
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if(report!!.areAllPermissionsGranted()){
                        requestLocationData()
                    }

                    if(report.isAnyPermissionPermanentlyDenied){
                        Toast.makeText(this@MainActivity, "You have denied location permissions", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationalDialogForPermissions()
                }
            }).onSameThread()
                .check()

        }
    }

    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this).setMessage("It looks like you have turned of permissions")
            .setPositiveButton(
                "GO TO SETTINGS"
            ){_ , _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package",packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }.setNegativeButton("Cancel"){dialog, _,-> dialog.dismiss()}.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val _locationRequest = LocationRequest()
        _locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        Looper.myLooper()?.let {
            _fusedLocationClient.requestLocationUpdates(
                _locationRequest, _locationCallback, it
            )
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if(Constants.isNetworkAvailable(this)){

            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()

            val service: WeatherService = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            listCall.enqueue(object: Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if(response!!.isSuccessful){
                        val weatherList: WeatherResponse? =  response.body()
                        Log.i("Response", "$weatherList")
                    }else{
                        val responseCode = response.code()
                        when(responseCode){
                            400 -> {
                                Log.e("Error 400","Bad Connction")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Error", t.stackTraceToString())
                }

            })
        }else{
            Toast.makeText(this@MainActivity, "No internet connection", Toast.LENGTH_SHORT).show()

        }
    }

    private val _locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult){
            val _lastLocation: Location = locationResult.lastLocation

            val _latitude = _lastLocation.latitude
            Log.i("current latitude", "$_latitude")

            val _longitude = _lastLocation.longitude
            Log.i("current longitude", "$_longitude")

            getLocationWeatherDetails(_latitude, _longitude)
        }
    }

    private fun isLocatinEnabled(): Boolean{
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}