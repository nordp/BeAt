const functions = require('firebase-functions');
const GeoFire = require('geofire')
const admin = require('firebase-admin');
const request = require('request');
const fs = require('fs');
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

exports.fetchSongData = functions.database
.ref('spots/{spotId}/track-id')
.onCreate(event => {

  return new Promise((resolve, reject) => {

    var client_id = fs.readFileSync('PUBLIC_KEY', 'utf8');
    var client_secret = fs.readFileSync('SECRET_KEY', 'utf8');
    var authHeader = 'Basic ' + (new Buffer(client_id + ':' + client_secret).toString('base64'));

    var authOptions = {
      url: 'https://accounts.spotify.com/api/token',
      headers: { 'Authorization': authHeader },
      form: {
        grant_type: 'client_credentials',
      },
      json: true
    };

    request.post(authOptions, function(error, response, body) {
      if (!error && response.statusCode === 200) {
        var accessToken = body.access_token;

        // TODO: Do spotify stuff

        resolve();
      } else {
        reject(error);
      }
    });

  });

});
