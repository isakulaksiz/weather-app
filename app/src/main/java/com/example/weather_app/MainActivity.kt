package com.example.weather_app

import android.annotation.SuppressLint
import android.app.Dialog
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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.weather_app.databinding.ActivityMainBinding
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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var _fusedLocationClient:  FusedLocationProviderClient
    private var _progressDialog: Dialog? = null

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            }.setNegativeButton("Cancel"){ dialog, _ -> dialog.dismiss()}.show()
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

            showProsgressDialog()

            listCall.enqueue(object: Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if(response!!.isSuccessful){

                        hideProgressDialog()

                        val weatherList: WeatherResponse? =  response.body()

                        if (weatherList != null) {
                            setUpUI(weatherList)
                        }

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
                    hideProgressDialog()
                }

            })
        }else{
            Toast.makeText(this@MainActivity, "No internet connection", Toast.LENGTH_SHORT).show()

        }
    }

    private val _locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult){
            val _lastLocation: Location = locationResult.lastLocation

            /* AŞKALE
            * lat 39.9214
            * lon 40.6922 */
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

    private fun showProsgressDialog(){
        _progressDialog = Dialog(this)

        _progressDialog!!.setContentView(R.layout.dialog_custom_progress)

        _progressDialog!!.show()
    }

    private fun hideProgressDialog(){
        if(_progressDialog != null)
            _progressDialog!!.dismiss()
    }

    private fun setUpUI(weatherList: WeatherResponse){
        for(i in weatherList.weather.indices){
            Log.i("Weather name", weatherList.weather.toString())
             binding.tvMain.text= weatherList.weather[i].main

            binding.tvMain.text = weatherList.weather[i].main
            binding.tvMainDescription.text = weatherList.weather[i].description
            // Santigrat veya ,Fahrenayt diplerini getUnit() fonk içerisinde tanımlandırılacak
            binding.tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())

            binding.tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
            binding.tvSunsetTime.text = unixTime(weatherList.sys.sunset)

            binding.tvHumidity.text = weatherList.main.humidity.toString() + " per cent"
            binding.tvMin.text = weatherList.main.temp_min.toString() + " min"
            binding.tvMax.text = weatherList.main.temp_max.toString() + " max"
            binding.tvSpeed.text = weatherList.wind.speed.toString()
            binding.tvName.text = weatherList.name
            binding.tvCountry.text = weatherList.sys.country

            when(weatherList.weather[i].icon){
                "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)
            }
        }
    }

    private fun getUnit(value: String): String?{
        var value = "°C"
        if("US" == value)
            value = "°F"

        return value
    }

    private fun unixTime(time: Long): String?{
        val date = Date(time*1000L)
        val trlocale = Locale("tr", "TR")
        val sdf = SimpleDateFormat("HH:mm", trlocale)
        sdf.timeZone = TimeZone.getDefault()

        return sdf.format(date)
    }
}