package edu.mtu.pjrussch.quicklog

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import edu.mtu.pjrussch.quicklog.API.APIController
import edu.mtu.pjrussch.quicklog.API.SDPAPI
import edu.mtu.pjrussch.quicklog.API.ServiceVolley
import kotlinx.android.synthetic.main.worklog_lookup.*
import kotlinx.android.synthetic.main.worklog_list_item.view.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import android.content.SharedPreferences
import android.text.Html
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.preference.PreferenceManager


class MainActivity : AppCompatActivity() {

    val LOG_TAG = "please"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.worklog_lookup)

        // E6A03771-4A4E-4DE3-95F7-E9DB0D780C36

        title = "Quicklog"
        super.setTheme(R.style.DefaultDarkTheme)

        // Clear the scrollview
        if (worklog_scrollview.childCount > 0)
            worklog_scrollview.removeAllViews()

        // Get a reference to the default app prefs
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Get a reference to our service and apiController
        val service = ServiceVolley(prefs.getString("sd_url", "https://servicedesk.mtu.edu/sdpapi/"))
        val apiController = APIController(service)

        Log.d(LOG_TAG, "Setting onClickListener for Button")

        // Go button to search for a requests worklogs
        search_worklog_button.setOnClickListener{
            hideKeyboard()
            setWorklogInputVisible(false)
            requestWorklogAndFillList(prefs, apiController)
        }

        // Assign the enter key to the same function as the go button
        request_input_field.setOnKeyListener { v, keycode, event ->
           if( event.action == KeyEvent.ACTION_DOWN && keycode == KeyEvent.KEYCODE_ENTER ) {
               hideKeyboard()
               setWorklogInputVisible(false)
               requestWorklogAndFillList(prefs, apiController)
               true
           } else
               false
        }

        worklog_submit_button.setOnClickListener{
            hideKeyboard()
            submitWorklog(worklog_input_field.text.toString(), prefs, apiController)
        }

        // Sets the edittexts to be done when you hit enter.
        request_input_field.imeOptions = EditorInfo.IME_ACTION_GO
        worklog_input_field.imeOptions = EditorInfo.IME_ACTION_GO

        // Hide the worklog submit stuff til we actually need it
        setWorklogInputVisible(false)

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
//        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        return when (item.itemId) {
            R.id.action_settings -> {
                val i = Intent(this, SettingsActivity::class.java)
                startActivity(i)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun setWorklogInputVisible(visible:Boolean) {
        when(visible) {
            true -> {
                worklog_input_field.visibility = View.VISIBLE
                worklog_submit_button.visibility = View.VISIBLE
            }

            false -> {
                worklog_input_field.visibility = View.GONE
                worklog_submit_button.visibility = View.GONE
            }
        }
    }


    /**
     * requestWorklogAndFillList(prefs, apiController)
     *  prefs: SharedPreferences - System shared prefs to retrieve API Key for the given user
     *  apiController: APIController - APIController to send post requests
     *
     * Clears the worklog scrollview and sets it to the worklogs for the given request
     *
     */
    fun requestWorklogAndFillList(prefs: SharedPreferences, apiController: APIController) {

        Log.d(LOG_TAG, "requestWorklogAndFillList() ")

        // Clear the scrollview
        if (worklog_scrollview.childCount > 0)
            worklog_scrollview.removeAllViews()

        // Create the loading icon and add to the scroll view
        val loadingView: View = layoutInflater.inflate(R.layout.worklog_loading_item, worklog_scrollview, false)
        loadingView.visibility = View.VISIBLE
        worklog_scrollview.addView(loadingView)

        // Grab the API Key and set the path to request the worklogs
        val apiKey = prefs.getString("api_key", "NO_API_KEY")
        val path = requestWorklogAllPath(Integer.parseInt(request_input_field.text.toString()), apiKey)

        if(apiKey != "NO_API_KEY") {
            apiController.post(path, JSONObject()) { response ->
                queryAndSetWorklogsView(response)
            }
        } else {
            addErrorScrollview("No API Key set, go to settings and set the API Key from ServiceDesk.", true)
        }
    }

    fun submitWorklog(worklogString:String, prefs:SharedPreferences, apiController: APIController) {

        // Grab the APIKey
        val apiKey = prefs.getString("api_key", "NO_API_KEY")

        // Create worklog JSON File to submit
        val inputData = JSONObject()
        val operationObj = inputData.put("operation", JSONObject()).getJSONObject("operation")
        val detailsObj = operationObj.put("details", JSONObject()).getJSONObject("details")
        val worklogsObj = detailsObj.put("worklogs", JSONObject()).getJSONObject("worklogs")
        val worklogObj = worklogsObj.put("worklog", JSONObject()).getJSONObject("worklog")
        worklogObj
            .put("description", "$worklogString")
            .put("workHours", "0")
            .put("workMinutes", "5")

        Log.d(LOG_TAG, "Generated worklog: ${inputData.toString()}")

        val path = requestAddWorklogPath(Integer.parseInt(request_input_field.text.toString()), apiKey, inputData)

        Log.d(LOG_TAG, "Button pressed, requesting worklogs from $path")

        if (apiKey != "NO_API_KEY") {
            apiController.post(path, JSONObject()) { response ->

                // Got response from the server, confirm that it was a success or failure and refresh
                // Clear the worklog input
                worklog_input_field.text.clear()

                // Refresh Path
                val path = requestWorklogAllPath(Integer.parseInt(request_input_field.text.toString()), apiKey)

                // Refresh list
                apiController.post(path, JSONObject()) { response ->
                    queryAndSetWorklogsView(response)
                }
            }
        }
    }

    fun hideKeyboard() {
        val inputManager:InputMethodManager = this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(this.currentFocus.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    fun addErrorScrollview(errorMessage:String, showRedError: Boolean) {
        if (worklog_scrollview.childCount > 0)
            worklog_scrollview.removeAllViews()

        val worklogView: View = layoutInflater.inflate(R.layout.worklog_list_item, worklog_scrollview, false)

        if(showRedError)
            worklogView.technican_field.text = Html.fromHtml("Status: <font color='#ff4611'> ERROR </font>")
        else
            worklogView.technican_field.text = Html.fromHtml("Status:")

        worklogView.date_field.text = SimpleDateFormat("MM/dd/yyyy hh:mm a").format(Date())
        worklogView.worklog_field.text = "$errorMessage"

        worklog_scrollview.addView(worklogView)
    }

    fun queryAndSetWorklogsView(response: JSONObject?) {
        if (worklog_scrollview.childCount > 0)
            worklog_scrollview.removeAllViews()

        Log.d(LOG_TAG, "Got response:\n${response.toString()}")
        val obj =  response?.getJSONObject("operation")

        if(obj != null) {

            // Clear the list of worklogs
            if (worklog_scrollview.childCount > 0)
                worklog_scrollview.removeAllViews()

            // Grab the status of the result
            val status:String = obj.getJSONObject("result").getString("status")
            val message:String = obj.getJSONObject("result").getString("message")
            Log.d(LOG_TAG, "Status from servicedesk: $status - $message")

            // Check if the request was successful, set error if not
            if(status == "Failed") {
                addErrorScrollview(message, true)
                setWorklogInputVisible(false)
                return
            }

            // Im lazy so this works for now
            try {
                val details:JSONArray? = obj.optJSONArray("Details")

                // Check if the request actually has worklogs
                if(details != null) {

                    val prefs = PreferenceManager.getDefaultSharedPreferences(this)

                    for(worklogIndex in if(!prefs.getBoolean("newestOrder", true)) (0..details.length()-1) else (details.length()-1 downTo 0 step 1)) {
                        Log.d(LOG_TAG, "Processing worklog $worklogIndex")

                        // Create a worklog view and get the object from the array of stuff
                        val worklogView: View = layoutInflater.inflate(R.layout.worklog_list_item, worklog_scrollview, false)
                        val worklogObj:JSONObject = details.getJSONObject(worklogIndex)

                        // Set the worklog object to the values returned from ServiceDesk
                        worklogView.technican_field.text = worklogObj.getString("technician")
                        val niceDate = SimpleDateFormat("MM/dd/yyyy hh:mm a").format(Date(worklogObj.getString("dateTime").toLong()))
                        worklogView.date_field.text = niceDate
                        worklogView.worklog_field.text = worklogObj.getString("description")

                        // Grab a reference to
                        worklog_scrollview.addView(worklogView)
                    }

                    // Scroll the scrollview to the newest
                    if(prefs.getBoolean("newestOrder", true)) {
                        worklog_scrollview_parent.fullScroll(View.FOCUS_UP)
                    } else {
                        worklog_scrollview_parent.fullScroll(View.FOCUS_DOWN)
                    }

                    setWorklogInputVisible(true)

                } else {
                    addErrorScrollview("Request has no worklogs", false)
                    setWorklogInputVisible(false)
                }

            } catch ( e: Exception) {
                Toast.makeText(this, "Request failed with exception: ${e}", Toast.LENGTH_LONG).show()
                setWorklogInputVisible(false)
            }

        } else {
            addErrorScrollview("Error while retieving worklogs", true)
            setWorklogInputVisible(false)
        }
    }

    fun requestWorklogAllPath(requestID:Int, APIKEY:String): String {
        return "request/$requestID/worklogs/?OPERATION_NAME=${SDPAPI.GET_WORKLOGS}&TECHNICIAN_KEY=$APIKEY&format=json"
    }

    fun requestAddWorklogPath(requestID:Int, APIKEY:String, inputData:JSONObject): String {
        return "request/$requestID/worklogs/?OPERATION_NAME=${SDPAPI.ADD_WORKLOG}&TECHNICIAN_KEY=$APIKEY&format=json&INPUT_DATA=${inputData.toString()}"
    }

}

