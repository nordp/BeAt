const functions = require('firebase-functions');
const GeoFire = require('geofire')
const admin = require('firebase-admin');
// // Create and Deploy Your First Cloud Functions
// // https://firebase.google.com/docs/functions/write-firebase-functions
//
// exports.helloWorld = functions.https.onRequest((request, response) => {
//  response.send("Hello from Firebase!");
// });

admin.initializeApp(functions.config().firebase);

exports.addSpot = functions.database
.ref('spots/{spotId}/loc')
.onCreate(event => {
    loc = event.data.val()

    lat = loc.lat;
    lon = loc.lon;

    geoFire = new GeoFire(admin.database().ref("/geofire"));
    location = [lat,lon];
    return new Promise((resolve, reject) => {
        resolve(geoFire.set(event.params.spotId, location));
    });
});

exports.removeSpot = functions.database
.ref('spots/{spotId}/loc')
.onDelete(event => {

    geoFire = new GeoFire(admin.database().ref("/geofire"));
    
    return new Promise((resolve, reject) => {
        geoFire.remove(event.params.spotId).then(function() {
        resolve();
      }, function(error) {
        reject(error);
      });
    
    });
});
