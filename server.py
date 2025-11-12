import os
# The correct import for the modern 'google-genai' library
from google import genai 
client = None

# --- Initialize the Gemini Client ---
# The client automatically picks up the GEMINI_API_KEY environment variable.
try:
    client = genai.Client()  
    print("Gemini client initialized successfully. Ready to build the /chat endpoint.")
except Exception as e:
    # This is the expected error if your GEMINI_API_KEY is not set yet.
    print(f"FATAL ERROR: Could not initialize Gemini client. Error: {e}")
    # You may want to stop the server from continuing if the client fails
    # raise e

    # Create a new chat session to maintain conversation history
chat_session = client.chats.create(model="gemini-2.5-flash")    

# Example of sending a message (replace with your server's logic):
user_message = "Hi, can you remember this conversation?"
response = chat_session.send_message(user_message)
print(response.text)