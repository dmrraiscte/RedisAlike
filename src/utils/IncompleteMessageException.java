package utils;

public class IncompleteMessageException extends Exception {
    public IncompleteMessageException() {
        super();
    }

    public IncompleteMessageException(String message) {
        super(message);
    }
}
