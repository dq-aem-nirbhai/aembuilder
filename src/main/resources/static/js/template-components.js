 function handleComponentClick(hasDesignDialog, event) {
            if (!hasDesignDialog) {
                event.preventDefault();
                alert("This component does not have a design dialog.");
            }
        }
