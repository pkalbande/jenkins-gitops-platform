from flask import Flask, jsonify
import os
from datetime import datetime
import time

app = Flask(__name__)

start_time = time.time()

@app.route('/')
def hello():
    return jsonify({
        'message': 'Hello from App2 Python!',
        'version': os.getenv('APP_VERSION', '1.0.0'),
        'environment': os.getenv('ENVIRONMENT', 'development'),
        'timestamp': datetime.now().isoformat()
    })

@app.route('/health')
def health():
    return jsonify({
        'status': 'healthy',
        'uptime': time.time() - start_time
    }), 200

@app.route('/ready')
def ready():
    return jsonify({
        'status': 'ready'
    }), 200

if __name__ == '__main__':
    port = int(os.getenv('PORT', 5000))
    app.run(host='0.0.0.0', port=port)
