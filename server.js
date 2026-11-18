const express = require('express');
const http = require('http');
const { Server } = require("socket.io");

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: "*" } });

app.get('/', (req, res) => res.send('S.Agent2 Server is Running!'));

io.on('connection', (socket) => {
    console.log('Device connected:', socket.id);

    // Host (Target Phone) room join karega
    socket.on('join-as-host', (roomId) => {
        socket.join(roomId);
        console.log(`Host joined room: ${roomId}`);
    });

    // Controller (Remote Phone) room join karega
    socket.on('join-as-controller', (roomId) => {
        socket.join(roomId);
        console.log(`Controller joined room: ${roomId}`);
    });

    // Touch events Controller se Host ko bhejna
    socket.on('touch-event', (data) => {
        // data mein roomId, x, y hoga
        socket.to(data.roomId).emit('perform-touch', data);
    });

    // Screen data Host se Controller ko bhejna (baad mein WebRTC ke liye)
    socket.on('screen-data', (data) => {
        socket.to(data.roomId).emit('receive-screen', data);
    });

    socket.on('disconnect', () => {
        console.log('Device disconnected:', socket.id);
    });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`Server listening on port ${PORT}`);
});
