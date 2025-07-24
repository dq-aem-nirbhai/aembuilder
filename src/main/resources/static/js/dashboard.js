setTimeout(() => {
        const success = document.getElementById('successAlert');
        const error = document.getElementById('errorAlert');

        if (success) {
            bootstrap.Alert.getOrCreateInstance(success).close();
        }
        if (error) {
            bootstrap.Alert.getOrCreateInstance(error).close();
        }
    }, 5000);