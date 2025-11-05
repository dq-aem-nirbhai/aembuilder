//alert("Hi code is working fine");

//This line displays an alert message with the text "Hi code is working fine". It's a way to notify the user that the script execution has
//started and is functioning properly.

function createContentFragment() {
	var form = $('form')[0]; // You need to use standard javascript object here
	var formData = new FormData(form);

//These lines select the first form element on the page using jQuery ($('form')[0]) and store it in the variable form. Then, 
//it creates a new FormData object named formData from the selected form. FormData objects are used to send form data to the server via AJAX requests.

    $(".results").html("");
	$.ajax({
        url: '/bin/content/createContentFragment',
        data: formData,
        method: 'POST',
        contentType: false, // NEEDED
        processData: false, // NEEDED

      // This block initiates an AJAX request using jQuery's $.ajax() method. It specifies the URL to which the request will be sent (url), 
      // the data to be sent (data), the HTTP method (method), and other settings.
      // contentType: false and processData: false are set to ensure that jQuery does not attempt to process the FormData object in any way. 
      //  This is necessary when sending FormData in an AJAX request.

		success: function(data) {
            console.log("Success");
			$(".results").html(data.status);
             //This line clears the content of the element with the class "results" using jQuery. It's clearing any previous results before making a new request.
            // jsout.name("status").value("Success Created total of " + resourceList.size() + " Content Fragments");(this is java line written in java).name=status, 
            //through this it prints the 

		}

        //If the AJAX request is successful, the success callback function is executed. It receives the response from the server as the data parameter.
       // Here, it logs "Success" to the console and updates the content of the element with the class "results" with the value of data.status.

        ,
        error: function(data) {data
            console.log("error", data);
            $(".results").html(data.responseText);
        }
       // If the AJAX request encounters an error, the error callback function is executed. It also receives the response from the server as the data parameter. 
       // Here, it logs the error data to the console and updates the content of the element with the class "results" with the error message (data.responseText).

	});

}