package tsp.headdb.core.api.exceptions;

public class APIOnlyException extends RuntimeException {
    public APIOnlyException() {
        super("This method is in the api-only disabled.");
    }
}
