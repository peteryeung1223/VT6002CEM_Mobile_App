const express = require('express');
const bodyParser = require('body-parser');
const { v4: uuidv4 } = require('uuid');
const { db } = require('./firebase');
const { collection, doc, addDoc, getDoc, getDocs, updateDoc, deleteDoc } = require('firebase/firestore');
const { getAuth, createUserWithEmailAndPassword, signInWithEmailAndPassword } = require('firebase/auth');
const { GoogleAuth } = require('google-auth-library');
const rateLimit = require('express-rate-limit');
const axios = require('axios');
require('dotenv').config();

const app = express();
const PORT = 3000;

app.use(bodyParser.json());

// Initialize Firebase Auth
const auth = getAuth();

// Firebase Admin Setup
const admin = require('firebase-admin');
if (!admin.apps.length) {
    admin.initializeApp({
        credential: admin.credential.cert({
            projectId: process.env.FIREBASE_PROJECT_ID,
            privateKey: process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n'),
            clientEmail: process.env.FIREBASE_CLIENT_EMAIL
        })
    });
}

// Rate Limiter
const configLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 100
});

// ==========================
// API Key Routes
// ==========================

// Weather API Key
app.get('/weather-api-key', (req, res) => {
    res.json({ apiKey: process.env.WEATHER_API_KEY });
});

// Trip News API Key
app.get('/trip-news-api-key', (req, res) => {
    res.json({ apiKey: process.env.TRIP_NEWS_API_KEY });
});

// ==========================
// Firebase Config
// ==========================
app.get('/firebase-config', configLimiter, (req, res) => {
    res.json({
        apiKey: process.env.FIREBASE_API_KEY,
        authDomain: process.env.FIREBASE_AUTH_DOMAIN,
        projectId: process.env.FIREBASE_PROJECT_ID,
        storageBucket: process.env.FIREBASE_STORAGE_BUCKET,
        messagingSenderId: process.env.FIREBASE_MESSAGING_SENDER_ID,
        appId: process.env.FIREBASE_APP_ID,
        measurementId: process.env.FIREBASE_MEASUREMENT_ID || null
    });
});

// Google Client IDs
app.get('/auth/google-client-ids', configLimiter, (req, res) => {
    res.json({
        androidClientId: process.env.GOOGLE_ANDROID_CLIENT_ID,
        iosClientId: process.env.GOOGLE_IOS_CLIENT_ID || null,
        expoClientId: process.env.GOOGLE_EXPO_CLIENT_ID || null,
        webClientId: process.env.GOOGLE_WEB_CLIENT_ID
    });
});

// Admin Secret
app.get('/admin-secret', configLimiter, (req, res) => {
    res.json({ secret: process.env.ADMIN_SECRET });
});

// ==========================
// Cloud Notification
// ==========================
app.post('/send-notification', async (req, res) => {
    const { title, body, topic, secret } = req.body;

    if (secret !== process.env.ADMIN_SECRET) {
        return res.status(403).json({ success: false, message: 'Unauthorized' });
    }

    if (!title || !body || !topic) {
        return res.status(400).json({ success: false, message: 'Missing title, body, or topic.' });
    }

    try {
        const SCOPES = ['https://www.googleapis.com/auth/firebase.messaging'];
        const authClient = new GoogleAuth({
            credentials: {
                client_email: process.env.FIREBASE_CLIENT_EMAIL,
                private_key: process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n'),
            },
            scopes: SCOPES,
        });

        const client = await authClient.getClient();
        const accessToken = await client.getAccessToken();

        const payload = {
            message: {
                topic: topic,
                notification: {
                    title: title,
                    body: body
                }
            }
        };

        const response = await axios.post(
            `https://fcm.googleapis.com/v1/projects/${process.env.FIREBASE_PROJECT_ID}/messages:send`,
            payload,
            {
                headers: {
                    'Authorization': `Bearer ${accessToken.token}`,
                    'Content-Type': 'application/json',
                }
            }
        );

        console.log('Notification sent:', response.data);
        res.status(200).json({ success: true, response: response.data });

    } catch (error) {
        console.error('Error sending notification:', error.message);
        res.status(500).json({ success: false, message: 'Failed to send notification.' });
    }
});

// ==========================
// Join Trip Notification
// ==========================
app.post('/join-trip', async (req, res) => {
    const { userId, tripId, tripSubject } = req.body;

    if (!userId || !tripId || !tripSubject) {
        return res.status(400).json({ success: false, message: 'Missing userId, tripId, or tripSubject.' });
    }

    try {
        const SCOPES = ['https://www.googleapis.com/auth/firebase.messaging'];
        const authClient = new GoogleAuth({
            credentials: {
                client_email: process.env.FIREBASE_CLIENT_EMAIL,
                private_key: process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n'),
            },
            scopes: SCOPES,
        });

        const client = await authClient.getClient();
        const accessToken = await client.getAccessToken();

        const payload = {
            message: {
                topic: 'admin_topic',
                notification: {
                    title: 'New Trip Join Request',
                    body: `User ${userId} has joined the trip: ${tripSubject}`
                },
                data: {
                    tripId: tripId,
                    userId: userId
                }
            }
        };

        console.log('Sending payload:', JSON.stringify(payload, null, 2));

        const response = await axios.post(
            `https://fcm.googleapis.com/v1/projects/${process.env.FIREBASE_PROJECT_ID}/messages:send`,
            payload,
            {
                headers: {
                    'Authorization': `Bearer ${accessToken.token}`,
                    'Content-Type': 'application/json',
                }
            }
        );

        console.log('Join notification sent:', response.data);
        res.status(200).json({ success: true });
    } catch (error) {
        console.error('Error sending join notification:', error.message);
        res.status(500).json({ success: false, message: 'Notification failed.' });
    }
});

// ==========================
// Email/Password Login
// ==========================
app.post('/login', async (req, res) => {
    const { email, password } = req.body;

    if (!email || !password)
        return res.status(400).json({ success: false, message: 'Email and password are required.' });

    try {
        const userCredential = await signInWithEmailAndPassword(auth, email, password);
        const token = await userCredential.user.getIdToken();
        res.status(200).json({ success: true, token, uid: userCredential.user.uid });
    } catch (error) {
        if (error.code === 'auth/user-not-found' || error.code === 'auth/invalid-credential') {
            try {
                const userCredential = await createUserWithEmailAndPassword(auth, email, password);
                const token = await userCredential.user.getIdToken();
                res.status(201).json({
                    success: true,
                    message: 'Account created and login successful!',
                    token,
                    uid: userCredential.user.uid,
                });
            } catch (regErr) {
                console.error('Registration error:', regErr);
                let msg = 'Registration failed. Please try again.';
                if (regErr.code === 'auth/invalid-email') msg = 'Invalid email.';
                if (regErr.code === 'auth/weak-password') msg = 'Weak password.';
                res.status(500).json({ success: false, message: msg });
            }
        } else {
            console.error('Login error:', error);
            res.status(401).json({ success: false, message: 'Login failed. Please try again.' });
        }
    }
});

// ==========================
// Google Login
// ==========================
app.post('/google-login', async (req, res) => {
    const { idToken } = req.body;

    if (!idToken) {
        return res.status(400).json({ success: false, message: 'ID token is required' });
    }

    try {
        const decodedToken = await admin.auth().verifyIdToken(idToken);
        const email = decodedToken.email;
        res.status(200).json({
            success: true,
            email,
            uid: decodedToken.uid,
        });
    } catch (err) {
        console.error('Google token verification failed:', err);
        res.status(401).json({ success: false, message: 'Invalid Google token' });
    }
});

// ==========================
// Start Server
// ==========================
app.listen(PORT, () => {
    console.log(`Server is running on http://localhost:${PORT}`);
});
