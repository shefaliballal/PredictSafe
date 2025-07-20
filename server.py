from flask import Flask, request, jsonify
from flask_cors import CORS
import requests
from textblob import TextBlob

app = Flask(__name__)
CORS(app)

NEWSAPI_KEY = "YOUR_NEWSAPI_KEY"  # Replace with your actual NewsAPI key
CRIME_KEYWORDS = ["robbery", "theft", "assault", "crime", "attack", "shooting", "murder", "burglary"]

def is_crime_related(text):
    text_low = text.lower()
    if any(keyword in text_low for keyword in CRIME_KEYWORDS):
        return True
    blob = TextBlob(text)
    return blob.sentiment.polarity < -0.3

@app.route('/analyze_news', methods=['POST'])
def analyze_news():
    data = request.json
    country = data.get("country", "in")  # default: India
    params = {
        "apiKey": NEWSAPI_KEY,
        "language": "en",
        "country": country,
        "pageSize": 20
    }
    try:
        resp = requests.get("https://newsapi.org/v2/top-headlines", params=params)
        resp.raise_for_status()
        docs = resp.json().get("articles", [])
    except Exception as e:
        return jsonify({"error": "Failed to fetch news", "details": str(e)}), 500

    threat_count, total = 0, 0

    for article in docs:
        total += 1
        text = (article.get("title") or "") + " " + (article.get("description") or "")
        if is_crime_related(text):
            threat_count += 1

    risk_score = threat_count / total if total > 0 else 0
    return jsonify({"risk_score": risk_score, "threat_count": threat_count, "total": total})

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8000)  # host=0.0.0.0 for LAN access
 